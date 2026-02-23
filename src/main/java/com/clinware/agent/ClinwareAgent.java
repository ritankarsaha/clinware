package com.clinware.agent;

import com.clinware.config.AgentConfig;
import com.clinware.tools.NewsToolExecutor;
import com.clinware.ui.AgentOutput;
import com.clinware.ui.TerminalOutput;
import com.google.genai.Client;
import com.google.genai.types.*;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;


public class ClinwareAgent {

    private static final Logger log = LoggerFactory.getLogger(ClinwareAgent.class);
    private static final int    MAX_TOOL_ITERATIONS       = 3;
    private static final long   GEMINI_RETRY_DEFAULT_MS   = 1_500L;
    private static final long   GEMINI_RETRY_RATELIMIT_MS = 15_000L;

    private final AgentConfig      config;
    private final NewsToolExecutor toolExecutor;
    private final AgentOutput      out;
    private final Client           geminiClient;
    private final Tool             mcpTool;         
    private final Tool             googleSearchTool; 
    private final Gson             gson = new Gson();

    
    private volatile AgentMode             mode;
    private volatile GenerateContentConfig genConfig;

    // persistent conversation history across all answer() calls
    private final List<Content> history = new ArrayList<>();

    // Constructors 

    public ClinwareAgent(AgentConfig config, NewsToolExecutor toolExecutor) {
        this(config, toolExecutor, new TerminalOutput());
    }

    public ClinwareAgent(AgentConfig config, NewsToolExecutor toolExecutor, AgentOutput out) {
        this(config, toolExecutor, out, AgentMode.from(config.getAgentModeRaw()));
    }

    public ClinwareAgent(AgentConfig config, NewsToolExecutor toolExecutor,
                         AgentOutput out, AgentMode initialMode) {
        this.config          = config;
        this.toolExecutor    = toolExecutor;
        this.out             = out;
        this.geminiClient    = new Client.Builder().apiKey(config.getGoogleApiKey()).build();
        this.mcpTool         = buildMcpTool();
        this.googleSearchTool = buildGoogleSearchTool();
        this.mode            = initialMode;
        this.genConfig       = buildGenConfig(initialMode);
    }

    // Mode switching 

    public synchronized void setMode(AgentMode newMode) {
        this.mode      = newMode;
        this.genConfig = buildGenConfig(newMode);
        log.info("Agent mode → {}", newMode);
    }

    public AgentMode getMode() { return mode; }

    // Public API

    public String answer(String userQuery) {
        log.info("Processing query: {}", userQuery);

        final int startIndex = history.size();
        history.add(Content.fromParts(Part.fromText(userQuery)));

        GenerateContentResponse response   = null;
        boolean                 toolCalled = false;
        int                     iterations = 0;
        boolean                 mcpFailed  = false; 

        try {
            while (iterations < MAX_TOOL_ITERATIONS) {
                iterations++;
                log.debug("Agent loop iteration {}/{}", iterations, MAX_TOOL_ITERATIONS);

                if (toolCalled) out.thinkingSynth();
                else            out.thinkingFirst();

                response = callGeminiWithRetry();
                if (response == null) {
                    rollbackToIndex(startIndex);
                    return PromptLibrary.SERVICE_UNAVAILABLE_RESPONSE;
                }

                List<FunctionCall> functionCalls = response.functionCalls();
                if (functionCalls == null || functionCalls.isEmpty()) {
                    log.info("No tool call — model answered directly (iteration {}).", iterations);
                    break;
                }

                FunctionCall functionCall = functionCalls.get(0);
                String       fnName       = functionCall.name().orElse("");
                JsonObject   fnArgs       = parseFunctionArgs(functionCall);

                Content modelTurn = response.candidates()
                        .flatMap(list -> list.isEmpty()
                                ? java.util.Optional.empty()
                                : java.util.Optional.of(list.get(0)))
                        .flatMap(Candidate::content)
                        .orElse(Content.fromParts(Part.fromText("")));
                history.add(modelTurn);

                NewsToolExecutor.ToolResult toolResult = toolExecutor.execute(fnName, fnArgs);
                toolCalled = true;

                if (!toolResult.found) {
                    if ("TIMEOUT".equals(toolResult.content) || "NO_RESULTS".equals(toolResult.content)) {
                        mcpFailed = true;
                        rollbackToIndex(startIndex);

                        // MCP found nothing - fall back to Google Search grounding
                        if (mode == AgentMode.HYBRID) {
                            log.info("HYBRID fallback: MCP {} → retrying with Google Search", toolResult.content);
                            return answerWithGroundingFallback(userQuery);
                        }

                        return "TIMEOUT".equals(toolResult.content)
                                ? PromptLibrary.MCP_TIMEOUT_RESPONSE
                                : PromptLibrary.NO_NEWS_RESPONSE;
                    }
                }

                Content functionResponseContent = Content.builder()
                        .role("user")
                        .parts(Part.fromFunctionResponse(fnName, Map.of("result", toolResult.content)))
                        .build();
                history.add(functionResponseContent);
            }

        } catch (Exception e) {
            log.error("Unexpected error in agent loop", e);
            rollbackToIndex(startIndex);
            return PromptLibrary.SERVICE_UNAVAILABLE_RESPONSE;
        }

        if (response == null) {
            rollbackToIndex(startIndex);
            return PromptLibrary.SERVICE_UNAVAILABLE_RESPONSE;
        }

        response.candidates()
                .flatMap(list -> list.isEmpty()
                        ? java.util.Optional.empty()
                        : java.util.Optional.of(list.get(0)))
                .flatMap(Candidate::content)
                .ifPresent(history::add);

        String finalAnswer = extractText(response);
        out.agentAnswer(finalAnswer);
        return finalAnswer;
    }

    /**
     * HYBRID fallback: re-runs the query using Google Search grounding only.
     */
    private String answerWithGroundingFallback(String userQuery) {
        out.warn("MCP returned no results — retrying with Google Search grounding…");

        final int fallbackStart = history.size();
        history.add(Content.fromParts(Part.fromText(userQuery)));

        out.thinkingFirst();

        // Use grounding-only config for this single call
        GenerateContentConfig groundingConfig = buildConfigForTool(googleSearchTool);
        GenerateContentResponse response;
        try {
            response = geminiClient.models.generateContent(config.getGeminiModel(), history, groundingConfig);
        } catch (Exception e) {
            log.error("Grounding fallback call failed", e);
            rollbackToIndex(fallbackStart);
            out.error("Google Search grounding also failed.");
            return PromptLibrary.SERVICE_UNAVAILABLE_RESPONSE;
        }

        if (response == null) {
            rollbackToIndex(fallbackStart);
            return PromptLibrary.SERVICE_UNAVAILABLE_RESPONSE;
        }

        response.candidates()
                .flatMap(list -> list.isEmpty()
                        ? java.util.Optional.empty()
                        : java.util.Optional.of(list.get(0)))
                .flatMap(Candidate::content)
                .ifPresent(history::add);

        String answer = extractText(response);
        out.agentAnswer(answer);
        return answer;
    }

    public void resetHistory() {
        history.clear();
        log.info("Conversation history cleared.");
    }

    public int historySize() { return history.size(); }

    public void setHistory(List<Content> loaded) {
        history.clear();
        history.addAll(loaded);
        log.info("History set to {} turns from persistent store.", history.size());
    }

    public List<Content> getHistory() { return Collections.unmodifiableList(history); }

    public List<Map.Entry<String, String>> getHistoryPairs(int maxCharsPerTurn) {
        List<Map.Entry<String, String>> pairs = new ArrayList<>();
        for (Content c : history) {
            String role = "?";
            try { role = c.role().orElse("?"); } catch (Exception ignored) {}
            String text = extractTextFromContent(c);
            if (text == null || text.isBlank()) continue;
            if (maxCharsPerTurn > 0 && text.length() > maxCharsPerTurn)
                text = text.substring(0, maxCharsPerTurn) + "…";
            pairs.add(Map.entry(role, text));
        }
        return pairs;
    }

    // Verge News MCP 
    private Tool buildMcpTool() {
        Schema keywordSchema = Schema.builder()
                .type("STRING")
                .description("Search keyword — e.g. 'Clinware', 'GLP-1 obesity drug', " +
                             "'AI diagnostics funding', 'post-acute care technology'")
                .build();
        Schema daysSchema = Schema.builder()
                .type("INTEGER")
                .description("How many days back to search (default " + config.getNewsSearchDays() + ")")
                .build();
        Schema paramsSchema = Schema.builder()
                .type("OBJECT")
                .properties(Map.of("keyword", keywordSchema, "days", daysSchema))
                .required("keyword")
                .build();
        FunctionDeclaration fn = FunctionDeclaration.builder()
                .name("searchNews")
                .description("Search for recent news articles. Use for: Clinware company news; " +
                             "healthcare market trends; disease research updates; FDA approvals; " +
                             "medical technology launches; funding rounds.")
                .parameters(paramsSchema)
                .build();
        return Tool.builder().functionDeclarations(List.of(fn)).build();
    }

    // Google Search grounding 
    private Tool buildGoogleSearchTool() {
        return Tool.builder().googleSearch(GoogleSearch.builder().build()).build();
    }

    // MCP and GoogleSearch cannot be in the same request
    private GenerateContentConfig buildGenConfig(AgentMode m) {
        Tool activeTool = (m == AgentMode.GROUNDING) ? googleSearchTool : mcpTool;
        return buildConfigForTool(activeTool);
    }

    private GenerateContentConfig buildConfigForTool(Tool tool) {
        return GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(PromptLibrary.SYSTEM_INSTRUCTION)))
                .tools(List.of(tool))
                .build();
    }

    //  History management

    private void rollbackToIndex(int index) {
        while (history.size() > index) history.remove(history.size() - 1);
        log.debug("History rolled back to {} entries.", index);
    }

    // Gemini call with retry 

    private GenerateContentResponse callGeminiWithRetry() {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return geminiClient.models.generateContent(config.getGeminiModel(), history, genConfig);
            } catch (Exception e) {
                if (attempt == 1) {
                    long delayMs = retryDelayFor(e);
                    log.warn("Gemini call failed (attempt 1), retrying in {}ms: {}", delayMs, e.getMessage());
                    out.warn("Gemini call failed — retrying in " + (delayMs / 1000) + "s…");
                    try { Thread.sleep(delayMs); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                } else {
                    log.error("Gemini call failed on both attempts", e);
                    out.error("Gemini API unavailable after retry.");
                }
            }
        }
        return null;
    }

    private static long retryDelayFor(Exception e) {
        String msg = e.getMessage();
        if (msg == null || !msg.contains("429")) return GEMINI_RETRY_DEFAULT_MS;
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("Please retry in ([0-9]+(?:\\.[0-9]+)?)s").matcher(msg);
        if (m.find()) {
            try { return (long)(Double.parseDouble(m.group(1)) * 1_000) + 1_000; }
            catch (NumberFormatException ignored) {}
        }
        return GEMINI_RETRY_RATELIMIT_MS;
    }

    // Helpers

    private JsonObject parseFunctionArgs(FunctionCall functionCall) {
        try {
            if (functionCall.args().isPresent())
                return gson.fromJson(gson.toJson(functionCall.args().get()), JsonObject.class);
        } catch (Exception e) { log.warn("Could not parse function args: {}", e.getMessage()); }
        return new JsonObject();
    }

    private String extractText(GenerateContentResponse response) {
        try {
            String text = response.text();
            if (text != null && !text.isBlank()) return text.trim();
        } catch (Exception e) { log.warn("Could not extract text from response: {}", e.getMessage()); }
        return PromptLibrary.SERVICE_UNAVAILABLE_RESPONSE;
    }

    private String extractTextFromContent(Content c) {
        try {
            JsonElement el = gson.toJsonTree(c);
            if (!el.isJsonObject()) return null;
            JsonElement parts = el.getAsJsonObject().get("parts");
            if (parts == null) return null;
            if (parts.isJsonArray()) {
                for (JsonElement p : parts.getAsJsonArray()) {
                    if (p.isJsonObject() && p.getAsJsonObject().has("text")) {
                        String t = p.getAsJsonObject().get("text").getAsString();
                        if (!t.isBlank()) return t.trim();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
