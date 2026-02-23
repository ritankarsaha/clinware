package com.clinware.ui;

public final class Terminal {

    private Terminal() {  }

    // ANSI Codes

    private static final String RESET   = "\033[0m";
    private static final String BOLD    = "\033[1m";
    private static final String DIM     = "\033[2m";
    private static final String GREEN   = "\033[32m";
    private static final String YELLOW  = "\033[33m";
    private static final String CYAN    = "\033[36m";
    private static final String BLUE    = "\033[34m";
    private static final String RED     = "\033[31m";
    private static final String MAGENTA = "\033[35m";

    /** True when the runtime environment is likely an ANSI-capable terminal. */
    static final boolean ANSI = detectAnsi();

    public static boolean hasAnsi() { return ANSI; }

    private static boolean detectAnsi() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac") || os.contains("linux") || os.contains("nix")) return true;
        return System.getenv("TERM") != null
            || System.getenv("COLORTERM") != null
            || System.getenv("WT_SESSION") != null;
    }

    private static String c(String text, String... codes) {
        if (!ANSI) return text;
        StringBuilder sb = new StringBuilder();
        for (String code : codes) sb.append(code);
        return sb.append(text).append(RESET).toString();
    }

    //  Layout

    public static void header(String text) {
        System.out.println();
        System.out.println(c("═".repeat(57), CYAN));
        System.out.println(c("  " + text, BOLD, CYAN));
        System.out.println(c("═".repeat(57), CYAN));
        System.out.println();
    }

    public static void divider() {
        System.out.println(c("─".repeat(57), DIM));
    }

    public static void blank() { System.out.println(); }

    // Status messages

    public static void ok(String text) {
        System.out.println(c("  ✔  ", GREEN, BOLD) + text);
    }

    public static void step(String text) {
        System.out.println(c("  →  ", CYAN) + c(text, CYAN));
    }

    public static void warn(String text) {
        System.out.println(c("  ⚠  ", YELLOW) + text);
    }

    public static void error(String text) {
        System.out.println(c("  ✗  ", RED, BOLD) + text);
    }

    public static void fatal(String text) {
        System.err.println(c("[FATAL] " + text, RED, BOLD));
    }

    public static void label(String label, String value) {
        System.out.println(c("       " + label + ": ", DIM) + c(value, BOLD));
    }


    /** Shown before the first Gemini call */
    public static void thinkingFirst() {
        System.out.println(c("  ✦  Sending query to Gemini...", DIM));
    }

    /** Shown before Gemini calls that follow a tool result injection. */
    public static void thinkingSynth() {
        System.out.println(c("  ✦  Synthesizing search results with Gemini...", DIM));
    }

    // Prompt / agent output

    public static void userPrompt() {
        System.out.print(c("\nYou › ", GREEN, BOLD));
        System.out.flush();
    }


    public static void agentAnswer(String text) {
        System.out.println();
        System.out.print(c("Agent › ", BLUE, BOLD));
        System.out.flush();

        String   rendered = MarkdownRenderer.render(text == null ? "" : text.trim());
        String[] lines    = rendered.split("\n", -1);

        int firstIdx = 0;
        while (firstIdx < lines.length && lines[firstIdx].isBlank()) firstIdx++;

        if (firstIdx < lines.length) {
            System.out.println(lines[firstIdx]);
            System.out.flush();
        }

        for (int i = firstIdx + 1; i < lines.length; i++) {
            System.out.println(lines[i]);
            System.out.flush();
            if (!lines[i].isBlank()) {
                try { Thread.sleep(15); }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                
                    while (++i < lines.length) System.out.println(lines[i]);
                    break;
                }
            }
        }
        System.out.println();
    }

    // Tool-call feedback

    public static void toolCalling(String toolName, String keyword) {
        System.out.println();
        System.out.println(c("  🔍 Tool call: ", YELLOW, BOLD)
                         + c(toolName + "(keyword=\"" + keyword + "\")", YELLOW));
    }

    public static void toolResult(boolean found, int count) {
        if (found) {
            System.out.println(c("  📰 Retrieved: ", GREEN)
                             + c(count + " result(s) — injecting into context", GREEN));
        } else {
            System.out.println(c("  📭 No articles found", YELLOW));
        }
    }

    public static void toolFallback(String newKeyword) {
        System.out.println(c("  ↩  Retrying with expanded keyword: \"" + newKeyword + "\"", MAGENTA));
    }

    // Typewriter 

    public static void typewriter(String text) {
        String[] tokens = text.split("(?<=\\s)|(?=\\s)");
        int charPos = 0;
        for (String token : tokens) {
            System.out.print(token);
            System.out.flush();
            charPos += token.length();
            if (!token.isBlank()) {
                try { Thread.sleep(18); }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                
                    if (charPos < text.length()) System.out.print(text.substring(charPos));
                    break;
                }
            }
        }
        System.out.println();
    }

    // Misc

    public static void print(String text)   { System.out.print(text); }
    public static void println(String text) { System.out.println(text); }
}
