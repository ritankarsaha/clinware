package com.clinware.tools;

import com.clinware.mcp.McpStdioClient;
import com.clinware.mcp.McpStdioClient.McpToolException;
import com.clinware.ui.AgentOutput;
import com.clinware.ui.TerminalOutput;
import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;


public class NewsToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(NewsToolExecutor.class);

    private static final String[] CLINWARE_FALLBACKS = {
        "Clinware AI",
        "post-acute care AI admissions",
        "hospital SNF transition technology"
    };


    private static final String[] HEALTHCARE_FALLBACKS = {
        "AI healthcare technology",
        "medical technology innovation",
        "digital health market trends"
    };

    //  Result wrapper

    public static class ToolResult {
        public final boolean found;
        public final String  content;
        public final int     articleCount;

        public ToolResult(boolean found, String content, int articleCount) {
            this.found        = found;
            this.content      = content;
            this.articleCount = articleCount;
        }

        // Convenience constructor for error/empty results
        public ToolResult(boolean found, String content) {
            this(found, content, 0);
        }
    }

    private final McpStdioClient mcpClient;
    private final int            defaultDays;
    private final AgentOutput    out;
    private final Gson           gson = new Gson();

    /** Backward-compatible constructor */
    public NewsToolExecutor(McpStdioClient mcpClient, int defaultDays) {
        this(mcpClient, defaultDays, new TerminalOutput());
    }

    public NewsToolExecutor(McpStdioClient mcpClient, int defaultDays, AgentOutput out) {
        this.mcpClient   = mcpClient;
        this.defaultDays = defaultDays;
        this.out         = out;
    }


    public ToolResult execute(String toolName, JsonObject args) {
        log.info("Executing tool '{}' with args: {}", toolName, args);

        String primaryKeyword = extractString(args, "keyword", "Clinware");
        int    days           = extractInt(args, "days", defaultDays);

        // Primary attempt
        out.toolCalling(toolName, primaryKeyword);
        ToolResult result = callMcp(primaryKeyword, days);

        if (result.found) {
            out.toolResult(true, result.articleCount);
            return result;
        }

        if ("TIMEOUT".equals(result.content) || result.content.startsWith("IO_ERROR")
                || result.content.startsWith("MCP_ERROR")) {
          
            out.toolResult(false, 0);
            return result;
        }

        // No results
        String[] fallbacks = primaryKeyword.toLowerCase().contains("clinware")
                ? CLINWARE_FALLBACKS : HEALTHCARE_FALLBACKS;

        for (String fallback : fallbacks) {
            out.toolFallback(fallback);
            ToolResult fallbackResult = callMcp(fallback, days);
            if (fallbackResult.found) {
                out.toolResult(true, fallbackResult.articleCount);
                // Prepend a note so Gemini knows we expanded the search
                String annotated = "[Note: No direct results for \"" + primaryKeyword
                    + "\". Showing results for \"" + fallback + "\" instead.]\n\n"
                    + fallbackResult.content;
                return new ToolResult(true, annotated, fallbackResult.articleCount);
            }
        }

        out.toolResult(false, 0);
        return new ToolResult(false, "NO_RESULTS");
    }

    // MCP call

    private ToolResult callMcp(String keyword, int days) {
        Map<String, Object> mcpParams = new HashMap<>();
        mcpParams.put("keyword", keyword);
        mcpParams.put("days", days);

        JsonElement rawResult;
        try {
            rawResult = mcpClient.callTool("search-news", mcpParams);
        } catch (TimeoutException e) {
            log.warn("MCP tool timed out for keyword '{}': {}", keyword, e.getMessage());
            return new ToolResult(false, "TIMEOUT");
        } catch (McpToolException e) {
            log.error("MCP tool error for keyword '{}': {}", keyword, e.getMessage());
            return new ToolResult(false, "MCP_ERROR: " + e.getMessage());
        } catch (IOException e) {
            log.error("I/O error calling MCP tool for keyword '{}'", keyword, e);
            return new ToolResult(false, "IO_ERROR: " + e.getMessage());
        }

        return parseAndFormat(rawResult, keyword);
    }

    ToolResult parseAndFormat(JsonElement raw, String keyword) {
        if (raw == null || raw.isJsonNull()) {
            log.info("MCP returned null result for keyword '{}'", keyword);
            return new ToolResult(false, "NO_RESULTS");
        }

        try {
            if (raw.isJsonObject()) {
                JsonObject obj = raw.getAsJsonObject();
                if (obj.has("content"))  return parseContentArray(obj.getAsJsonArray("content"), keyword);
                if (obj.has("articles")) return parseArticlesArray(obj.getAsJsonArray("articles"), keyword);
                // Raw object fallback
                String text = obj.toString();
                return text.isBlank()
                    ? new ToolResult(false, "NO_RESULTS")
                    : new ToolResult(true, text, 1);
            }

            if (raw.isJsonArray()) return parseContentArray(raw.getAsJsonArray(), keyword);

            String text = raw.getAsString().trim();
            return text.isBlank()
                ? new ToolResult(false, "NO_RESULTS")
                : new ToolResult(true, text, 1);

        } catch (Exception e) {
            log.error("Failed to parse MCP result for keyword '{}'", keyword, e);
            return new ToolResult(false, "PARSE_ERROR: " + e.getMessage());
        }
    }

    private ToolResult parseContentArray(JsonArray contentArray, String keyword) {
        if (contentArray == null || contentArray.isEmpty()) {
            return new ToolResult(false, "NO_RESULTS");
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (JsonElement el : contentArray) {
            if (el.isJsonObject()) {
                JsonObject item = el.getAsJsonObject();
                String type = item.has("type") ? item.get("type").getAsString() : "";
                if ("text".equals(type) && item.has("text")) {
                    sb.append(item.get("text").getAsString()).append("\n\n");
                    count++;
                }
            } else if (el.isJsonPrimitive()) {
                sb.append(el.getAsString()).append("\n\n");
                count++;
            }
        }
        String result = sb.toString().trim();
        if (result.isBlank()) return new ToolResult(false, "NO_RESULTS");
        log.info("Parsed {} content items for keyword '{}'", count, keyword);
        return new ToolResult(true, result, count);
    }

    private ToolResult parseArticlesArray(JsonArray articles, String keyword) {
        if (articles == null || articles.isEmpty()) {
            return new ToolResult(false, "NO_RESULTS");
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (JsonElement el : articles) {
            if (!el.isJsonObject()) continue;
            JsonObject article = el.getAsJsonObject();

            String title   = getString(article, "title",   "(No title)");
            String source  = getString(article, "source",  "Unknown source");
            String date    = getString(article, "date",    "Unknown date");
            String url     = getString(article, "url",     "");
            String summary = getString(article, "summary",
                             getString(article, "description", "No summary available."));

            sb.append("Title:   ").append(title).append("\n");
            sb.append("Source:  ").append(source).append("\n");
            sb.append("Date:    ").append(date).append("\n");
            if (!url.isBlank()) sb.append("URL:     ").append(url).append("\n");
            sb.append("Summary: ").append(summary).append("\n\n");
            count++;
        }
        String result = sb.toString().trim();
        if (result.isBlank()) return new ToolResult(false, "NO_RESULTS");
        log.info("Formatted {} articles for keyword '{}'", count, keyword);
        return new ToolResult(true, result, count);
    }

    // Arg extraction helpers

    private String extractString(JsonObject obj, String key, String defaultVal) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return defaultVal;
        try { return obj.get(key).getAsString(); } catch (Exception e) { return defaultVal; }
    }

    private int extractInt(JsonObject obj, String key, int defaultVal) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return defaultVal;
        try { return obj.get(key).getAsInt(); } catch (Exception e) { return defaultVal; }
    }

    private String getString(JsonObject obj, String key, String defaultVal) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return defaultVal;
        try { return obj.get(key).getAsString(); } catch (Exception e) { return defaultVal; }
    }
}
