package com.clinware.ui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class MarkdownRenderer {

    private MarkdownRenderer() {}


    private static final String RESET  = "\033[0m";
    private static final String BOLD   = "\033[1m";
    private static final String DIM    = "\033[2m";
    private static final String ITALIC = "\033[3m";
    private static final String CYAN   = "\033[36m";
    private static final String BLUE   = "\033[34m";
    private static final String YELLOW = "\033[33m";

    // Public entry point

    public static String render(String text) {
        if (text == null || text.isBlank()) return text == null ? "" : text;
        if (!Terminal.hasAnsi()) return stripMarkdown(text);

        String[]      lines       = text.split("\n", -1);
        StringBuilder out         = new StringBuilder();
        boolean       inCodeBlock = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            if (line.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                out.append(DIM).append(line).append(RESET);
            } else if (inCodeBlock) {

                out.append(DIM).append("  ").append(line).append(RESET);
            } else {
                out.append(renderLine(line));
            }

            if (i < lines.length - 1) out.append('\n');
        }
        return out.toString();
    }

    // Line-level rendering

    private static String renderLine(String line) {
     
        if (line.matches("^# .+")) {
            return BOLD + CYAN + "══ " + line.substring(2).toUpperCase() + " ══" + RESET;
        }
        
        if (line.matches("^## .+")) {
            return BOLD + CYAN + "── " + line.substring(3) + " ──" + RESET;
        }
       
        if (line.matches("^### .+")) {
            return BOLD + BLUE + line.substring(4) + RESET;
        }
       
        if (line.matches("^#{4,} .+")) {
            int space = line.indexOf(' ');
            return BOLD + line.substring(space + 1) + RESET;
        }
        
        if (line.matches("^[-*_]{3,}\\s*$")) {
            return DIM + "─".repeat(54) + RESET;
        }
       
        if (line.startsWith("> ")) {
            return DIM + "│ " + renderInline(line.substring(2)) + RESET;
        }
        
        if (line.matches("^  [-*+] .+")) {
            return "    " + DIM + "◦" + RESET + " " + renderInline(line.substring(4));
        }
   
        if (line.matches("^[-*+] .+")) {
            return "  " + CYAN + "•" + RESET + " " + renderInline(line.substring(2));
        }
 
        if (line.matches("^\\d+\\. .+")) {
            int dot  = line.indexOf(". ");
            String num  = line.substring(0, dot + 1);  
            String rest = line.substring(dot + 2);
            return "  " + BOLD + num + RESET + " " + renderInline(rest);
        }
     
        if (line.isBlank()) return "";
  
        return renderInline(line);
    }

   

   
    private static String renderInline(String text) {
        
        text = replace(text, Pattern.compile("`([^`]+)`"),
                m -> YELLOW + m.group(1) + RESET);

    
        text = replace(text, Pattern.compile("\\*\\*\\*([^*]+)\\*\\*\\*"),
                m -> BOLD + ITALIC + m.group(1) + RESET);

        
        text = replace(text, Pattern.compile("\\*\\*([^*]+)\\*\\*"),
                m -> BOLD + m.group(1) + RESET);

      
        text = replace(text,
                Pattern.compile("(?<!\\*)\\*(?!\\*)([^*\n]+?)(?<!\\*)\\*(?!\\*)"),
                m -> ITALIC + m.group(1) + RESET);

        return text;
    }

    

    @FunctionalInterface
    private interface Replacer { String apply(Matcher m); }

    private static String replace(String text, Pattern p, Replacer r) {
        Matcher       m  = p.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(r.apply(m)));
        m.appendTail(sb);
        return sb.toString();
    }

    public static String stripMarkdown(String text) {
        return text
                .replaceAll("\\*\\*\\*([^*]+)\\*\\*\\*", "$1")
                .replaceAll("\\*\\*([^*]+)\\*\\*",        "$1")
                .replaceAll("\\*([^*\n]+)\\*",             "$1")
                .replaceAll("`([^`]+)`",                   "$1")
                .replaceAll("(?m)^```[^\\n]*\\n?",         "")
                .replaceAll("(?m)^#{1,6} ",                "")
                .replaceAll("(?m)^[-*+] ",                 "• ")
                .replaceAll("(?m)^> ",                     "  ")
                .replaceAll("(?m)^[-*_]{3,}\\s*$",         "──────────────────────────────────────────────────────");
    }
}
