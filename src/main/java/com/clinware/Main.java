package com.clinware;

import com.clinware.agent.AgentMode;
import com.clinware.agent.ClinwareAgent;
import com.clinware.agent.SessionStore;
import com.clinware.config.AgentConfig;
import com.clinware.mcp.McpStdioClient;
import com.clinware.tools.NewsToolExecutor;
import com.clinware.ui.AgentOutput;
import com.clinware.ui.AgentWindow;
import com.clinware.ui.QueryConsole;
import com.clinware.ui.Terminal;
import com.clinware.ui.TerminalOutput;

import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import com.google.genai.types.Content;

/**
 * Entry point — wires every layer together and drives the user interface.
 *
 * Startup sequence:
 *   Load AgentConfig  (validates GOOGLE_API_KEY, exits with clear message if missing)
 *   Start McpStdioClient (spawns MCP process, performs JSON-RPC handshake)
 *   Build ClinwareAgent (injects config + MCP executor + active mode)
 *   Run the demo query to show the agent working immediately
 *   Enter interactive REPL with persistent conversation memory
 *   Shutdown MCP process cleanly on exit
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private static final String DEMO_QUERY =
        "What are the latest news and updates about Clinware AI?";

    public static void main(String[] args) {

        // Suppress noisy JUL WARNING from Gemini SDK for function-call history turns
        java.util.logging.Logger.getLogger("com.google.genai.types")
                .setLevel(java.util.logging.Level.SEVERE);

        Terminal.header("Clinware Intelligence Agent  v2.0");

        //  Configuration 
        AgentConfig config;
        try {
            config = new AgentConfig();
        } catch (Exception e) {
            Terminal.fatal("Failed to load configuration: " + e.getMessage());
            System.exit(1);
            return;
        }
        AgentMode mode = AgentMode.from(config.getAgentModeRaw());
        Terminal.ok("Configuration loaded");
        Terminal.label("Model",   config.getGeminiModel());
        Terminal.label("Mode",    mode.label);
        Terminal.label("Timeout", config.getMcpTimeoutMs() + " ms");
        Terminal.label("Days",    config.getNewsSearchDays() + " days back");
        Terminal.blank();

        //  MCP Startup
        McpStdioClient mcpClient = new McpStdioClient(config.getMcpTimeoutMs());
        try {
            Terminal.step("Spawning MCP news server (verge-news-mcp/build/index.js)...");
            mcpClient.start();
            Terminal.ok("MCP handshake complete — JSON-RPC channel open");
            if (mode == AgentMode.GROUNDING)
                Terminal.println("  ℹ  GROUNDING mode active — MCP server running but not used");
            Terminal.blank();
        } catch (IOException e) {
            Terminal.fatal("Could not start MCP server.");
            Terminal.println("       Make sure Node.js is installed:");
            Terminal.println("         brew install node   (macOS)");
            Terminal.println("         Details: " + e.getMessage());
            System.exit(1);
            return;
        }

        // Wire the agent
        boolean guiMode = hasArg(args, "--gui");

        AgentWindow guiWindow = null;
        if (guiMode) {
            AgentWindow[] ref = {null};
            try {
                SwingUtilities.invokeAndWait(() -> ref[0] = new AgentWindow());
            } catch (Exception e) {
                Terminal.fatal("Failed to create GUI window: " + e.getMessage());
                System.exit(1);
            }
            guiWindow = ref[0];
        }

        AgentOutput output = guiMode ? guiWindow : new TerminalOutput();

        NewsToolExecutor toolExecutor = new NewsToolExecutor(mcpClient, config.getNewsSearchDays(), output);
        ClinwareAgent    agent        = new ClinwareAgent(config, toolExecutor, output, mode);

        //  Restore previous session
        Path          historyPath  = SessionStore.defaultPath();
        List<Content> savedHistory = SessionStore.load(historyPath);
        if (!savedHistory.isEmpty()) {
            agent.setHistory(savedHistory);
            Terminal.ok("Session restored — " + savedHistory.size() + " turns from previous session");
            Terminal.label("History", historyPath.toString());
            Terminal.blank();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> SessionStore.save(agent.getHistory(), historyPath), "clinware-session-save"));
        Runtime.getRuntime().addShutdownHook(new Thread(
                mcpClient::close, "clinware-mcp-shutdown"));

        // GUI mode 
        boolean sessionRestored = !savedHistory.isEmpty();
        if (guiMode) {
            final AgentWindow win     = guiWindow;
            final boolean     runDemo = !sessionRestored || hasArg(args, "--demo");

            win.setAgent(agent);
            SwingUtilities.invokeLater(() -> {
                win.showWelcome(sessionRestored);
                win.setVisible(true);
                if (runDemo) win.runDemoQuery(DEMO_QUERY);
            });

            try { Thread.currentThread().join(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return;
        }

        //  Demo query
        if (!sessionRestored || hasArg(args, "--demo")) {
            Terminal.divider();
            Terminal.step("Demo query: \"" + DEMO_QUERY + "\"");
            Terminal.divider();
            agent.answer(DEMO_QUERY);
        } else {
            Terminal.divider();
            Terminal.step("Skipping demo query — previous session restored. Type your question or 'exit'.");
            Terminal.divider();
        }

        //  Interactive REPL
        if (!hasArg(args, "--no-interactive")) {
            runInteractiveLoop(agent, args);
        }

        Terminal.blank();
        Terminal.divider();
        Terminal.ok("Goodbye.");
    }

    // Interactive REPL

    private static void runInteractiveLoop(ClinwareAgent agent, String[] args) {
        Terminal.blank();
        Terminal.divider();
        Terminal.println("  Interactive mode — conversation memory is active across turns.");
        Terminal.println("  Type /help for commands, /mode to switch search tools.");
        Terminal.divider();

        QueryConsole console = new QueryConsole();

        while (true) {
            String input = console.readLine();
            if (input == null) break;
            input = input.trim();
            if (input.isBlank()) continue;

            if ("exit".equalsIgnoreCase(input) || "quit".equalsIgnoreCase(input)) break;

            if ("/help".equalsIgnoreCase(input))    { printHelp(agent);    continue; }
            if ("/reset".equalsIgnoreCase(input))   { agent.resetHistory(); Terminal.ok("Memory cleared."); continue; }
            if ("/history".equalsIgnoreCase(input)) { printHistory(agent); continue; }
            if ("/save".equalsIgnoreCase(input))    { SessionStore.save(agent.getHistory(), SessionStore.defaultPath()); Terminal.ok("Session saved → " + SessionStore.defaultPath()); continue; }
            if ("/clear".equalsIgnoreCase(input))   { System.out.print("\033[H\033[2J"); System.out.flush(); continue; }

            if (input.toLowerCase().startsWith("/mode")) {
                String[] parts = input.split("\\s+", 2);
                if (parts.length > 1) {
                    AgentMode m = AgentMode.from(parts[1]);
                    agent.setMode(m);
                    Terminal.ok("Mode → " + m.label);
                } else {
                    Terminal.label("Mode", agent.getMode().label);
                    Terminal.println("  Usage: /mode mcp | /mode grounding | /mode hybrid");
                }
                continue;
            }

            agent.answer(input);
        }
    }

    // /help

    private static void printHelp(ClinwareAgent agent) {
        Terminal.blank();
        Terminal.header("Clinware AI — Feature Guide");

        Terminal.println("  COMMANDS");
        Terminal.println("  ────────────────────────────────────────────────────");
        Terminal.label("  /help             ", "Show this guide");
        Terminal.label("  /mode             ", "Show current search mode");
        Terminal.label("  /mode mcp         ", "Switch to Verge News MCP only");
        Terminal.label("  /mode grounding   ", "Switch to Google Search grounding only");
        Terminal.label("  /mode hybrid      ", "Switch to MCP + Google Search (default)");
        Terminal.label("  /history          ", "Print conversation turns + file location");
        Terminal.label("  /save             ", "Flush current session to disk right now");
        Terminal.label("  /reset            ", "Wipe conversation memory (fresh session)");
        Terminal.label("  /clear            ", "Clear the terminal screen");
        Terminal.label("  exit / quit       ", "Quit (session auto-saved)");
        Terminal.blank();

        Terminal.println("  CURRENT MODE:  " + agent.getMode().label);
        Terminal.blank();

        Terminal.println("  SEARCH MODES");
        Terminal.println("  ────────────────────────────────────────────────────");
        Terminal.println("  mcp       — Verge News MCP via JSON-RPC stdio (assignment baseline)");
        Terminal.println("  grounding — Google Search grounding (real-time web, no MCP call)");
        Terminal.println("  hybrid    — Both tools active; Gemini picks the best one");
        Terminal.blank();

        Terminal.println("  INPUT NAVIGATION  (raw-terminal mode, macOS / Linux)");
        Terminal.println("  ────────────────────────────────────────────────────");
        Terminal.label("  ↑ / ↓      ", "Cycle through past queries");
        Terminal.label("  Ctrl-U     ", "Clear the current input line");
        Terminal.label("  Ctrl-C     ", "Exit (session auto-saved)");
        Terminal.blank();

        Terminal.println("  CONFIGURATION  (env vars or .clinware.yml)");
        Terminal.println("  ────────────────────────────────────────────────────");
        Terminal.label("  GOOGLE_API_KEY    ", "Gemini API key (required)");
        Terminal.label("  GEMINI_MODEL      ", "Model name  (default: gemini-2.5-flash)");
        Terminal.label("  AGENT_MODE        ", "mcp | grounding | hybrid  (default: hybrid)");
        Terminal.label("  MCP_TIMEOUT_MS    ", "Tool timeout ms  (default: 15000)");
        Terminal.label("  NEWS_SEARCH_DAYS  ", "How many days back to search  (default: 30)");
        Terminal.blank();

        Terminal.println("  LAUNCH FLAGS");
        Terminal.println("  ────────────────────────────────────────────────────");
        Terminal.label("  --gui             ", "Launch Swing GUI window");
        Terminal.label("  --demo            ", "Force demo query even on restored session");
        Terminal.label("  --no-interactive  ", "Run demo query only, then exit");
        Terminal.blank();
    }

    //  /history

    private static void printHistory(ClinwareAgent agent) {
        List<Map.Entry<String, String>> pairs = agent.getHistoryPairs(200);
        if (pairs.isEmpty()) { Terminal.warn("No conversation history in this session."); return; }

        Terminal.blank();
        Terminal.println("  CONVERSATION HISTORY  (" + pairs.size() + " turns)");
        Terminal.println("  File: " + SessionStore.defaultPath());
        Terminal.divider();
        int turn = 1;
        for (Map.Entry<String, String> p : pairs) {
            boolean isUser = !"model".equalsIgnoreCase(p.getKey());
            System.out.println("  [" + turn++ + "] " + (isUser ? "You   " : "Agent ") + "› " + p.getValue());
        }
        Terminal.divider();
    }

    // Utility

    private static boolean hasArg(String[] args, String flag) {
        for (String arg : args) if (flag.equalsIgnoreCase(arg)) return true;
        return false;
    }
}
