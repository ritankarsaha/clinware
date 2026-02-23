package com.clinware.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;


public final class QueryConsole {

    private static final Logger log = LoggerFactory.getLogger(QueryConsole.class);


    private static final String RESET     = "\033[0m";
    private static final String BOLD      = "\033[1m";
    private static final String GREEN     = "\033[32m";
    private static final String CLEAR_EOL = "\033[K";     

    private static final String PROMPT_PLAIN = "You › ";
 
    private static final String PROMPT_ANSI  = GREEN + BOLD + PROMPT_PLAIN + RESET;

    /** Past user inputs collected during this JVM session. */
    private final List<String> inputHistory = new ArrayList<>();

    private final boolean rawSupported;

    private volatile String savedStty = null;

    // Constructor

    public QueryConsole() {
        this.rawSupported = Terminal.hasAnsi() && isTty() && isUnixLike();
        if (rawSupported) {

            // Guarantee terminal restoration even on abnormal JVM exit
            Runtime.getRuntime().addShutdownHook(
                    new Thread(this::restoreTerminal, "clinware-tty-restore"));
        }
    }

    

    /**
     * Prints the prompt and reads one line of user input.
     */
    public String readLine() {
        return rawSupported ? readLineRaw() : readLineSimple();
    }

    // Raw-mode reader

    private String readLineRaw() {
        enterRawMode();
        if (savedStty == null) {

        
            return readLineSimple();
        }

        // Print the prompt
        System.out.print("\n" + PROMPT_ANSI);
        System.out.flush();

        StringBuilder line    = new StringBuilder();
        StringBuilder partial = new StringBuilder(); 
        int           histPos = -1;                 

        try (FileInputStream tty = new FileInputStream("/dev/tty")) {
            while (true) {
                int b = tty.read();
                if (b < 0) return null;  

                // Enter 
                if (b == '\r' || b == '\n') {
                    System.out.println();
                    System.out.flush();
                    break;
                }

                // Backspace / DEL
                if (b == 127 || b == 8) {
                    if (line.length() > 0) {
                        // Remove last code-point 
                        int lastIdx = line.length() - 1;
                       
                        if (lastIdx > 0 && Character.isLowSurrogate(line.charAt(lastIdx))
                                && Character.isHighSurrogate(line.charAt(lastIdx - 1))) {
                            line.deleteCharAt(lastIdx);
                        }
                        line.deleteCharAt(line.length() - 1);
                        System.out.print("\b \b");
                        System.out.flush();
                    }
                    continue;
                }

                // Ctrl-C
                if (b == 3) {
                    System.out.println();
                    return null;
                }

                // Ctrl-D 
                if (b == 4) {
                    if (line.length() == 0) return null;
                    // Non-empty buffer: ignore (delete-forward not implemented)
                    continue;
                }

                // Ctrl-U 
                if (b == 21) {
                    clearInputDisplay();
                    line.setLength(0);
                    histPos = -1;
                    continue;
                }

                // ESC sequence
                if (b == 27) {
                    int b2 = tty.read();
                    if (b2 != '[') continue; 

                    int b3 = tty.read();
                    switch (b3) {

                        case 'A' -> {   //  Up — go to older history entry
                            int max = inputHistory.size() - 1;
                            if (histPos < max) {
                                if (histPos == -1) {
                                    
                                    partial.setLength(0);
                                    partial.append(line);
                                }
                                histPos++;
                                String entry = inputHistory.get(inputHistory.size() - 1 - histPos);
                                clearInputDisplay();
                                line.setLength(0);
                                line.append(entry);
                                System.out.print(entry);
                                System.out.flush();
                            }
                        }

                        case 'B' -> {   //  Down — go to newer entry 
                            if (histPos >= 0) {
                                histPos--;
                                String entry = (histPos == -1)
                                        ? partial.toString()
                                        : inputHistory.get(inputHistory.size() - 1 - histPos);
                                clearInputDisplay();
                                line.setLength(0);
                                line.append(entry);
                                System.out.print(entry);
                                System.out.flush();
                            }
                        }

                        default -> {  }
                    }
                    continue;
                }

                // Printable ASCII 
                if (b >= 32 && b < 127) {
                    char c = (char) b;
                    line.append(c);
                    System.out.print(c);
                    System.out.flush();
                    continue;
                }

                // UTF-8 multi-byte sequence
                if (b >= 0x80) {
                    int extra;
                    if      ((b & 0xE0) == 0xC0) extra = 1;
                    else if ((b & 0xF0) == 0xE0) extra = 2;
                    else if ((b & 0xF8) == 0xF0) extra = 3;
                    else continue; 

                    byte[] seq = new byte[1 + extra];
                    seq[0] = (byte) b;
                    for (int i = 0; i < extra; i++) {
                        int nb = tty.read();
                        if (nb < 0) break;
                        seq[i + 1] = (byte) nb;
                    }
                    String ch = new String(seq, StandardCharsets.UTF_8);
                    line.append(ch);
                    System.out.write(seq);
                    System.out.flush();
                }
            }
        } catch (IOException e) {
            log.error("Error reading from /dev/tty in raw mode", e);
            return null;
        } finally {
            restoreTerminal();
        }

        String result = line.toString().trim();
        if (!result.isEmpty()) inputHistory.add(result);
        return result;
    }

    private void clearInputDisplay() {
        System.out.print("\r" + PROMPT_ANSI + CLEAR_EOL);
        System.out.flush();
    }

    // Simple fallback 

    private final BufferedReader simpleReader =
            new BufferedReader(new InputStreamReader(System.in));

    private String readLineSimple() {
        System.out.print("\n" + PROMPT_ANSI);
        System.out.flush();
        try {
            String line = simpleReader.readLine();
            if (line != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) inputHistory.add(trimmed);
            }
            return line;
        } catch (IOException e) {
            return null;
        }
    }

    // Terminal raw-mode management 

    private void enterRawMode() {
        try {

            // Capture current settings so we can restore exactly
            Process save = new ProcessBuilder("stty", "-g")
                    .redirectInput(new File("/dev/tty"))
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            String settings = new String(save.getInputStream().readAllBytes()).trim();
            save.waitFor();
            savedStty = settings.isEmpty() ? null : settings;
            if (savedStty == null) return; 

            // Enter raw mode
            new ProcessBuilder("stty", "raw", "-echo")
                    .redirectInput(new File("/dev/tty"))
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start().waitFor();
        } catch (Exception e) {
            log.warn("Could not enter raw terminal mode: {}", e.getMessage());
            savedStty = null;
        }
    }

    void restoreTerminal() {
        try {
            String[] cmd = (savedStty != null && !savedStty.isEmpty())
                    ? new String[]{"stty", savedStty}
                    : new String[]{"stty", "sane"};
            new ProcessBuilder(cmd)
                    .redirectInput(new File("/dev/tty"))
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start().waitFor();
        } catch (Exception e) {
            
        }
    }

    // Environment helpers

    private static boolean isTty() {
        return new File("/dev/tty").exists();
    }

    private static boolean isUnixLike() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("mac") || os.contains("linux") || os.contains("nix");
    }
}
