package com.clinware.ui;

import com.clinware.agent.AgentMode;
import com.clinware.agent.ClinwareAgent;
import com.clinware.agent.SessionStore;
import com.clinware.agent.SessionStore.SessionMeta;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public final class AgentWindow extends JFrame implements AgentOutput {

    // Palette
    private static final Color BG_DARK    = new Color(28,  28,  28);
    private static final Color BG_PANEL   = new Color(38,  38,  38);
    private static final Color BG_SIDEBAR = new Color(33,  33,  33);
    private static final Color BG_INPUT   = new Color(48,  48,  48);
    private static final Color BG_SEL     = new Color(50,  90, 150);
    private static final Color DIVIDER    = new Color(55,  55,  55);
    private static final Color FG_USER    = new Color(100, 200, 120);
    private static final Color FG_AGENT   = new Color(100, 180, 255);
    private static final Color FG_STATUS  = new Color(150, 150, 150);
    private static final Color FG_TOOL    = new Color(255, 200,  80);
    private static final Color FG_DIM     = new Color(95,  95,  95);

    // Chat
    private final JTextPane      chat;
    private final StyledDocument doc;
    private final JTextField     input;
    private final JButton        sendBtn;

    // Styles
    private final Style userStyle;
    private final Style agentStyle;
    private final Style statusStyle;
    private final Style toolStyle;

    // Sidebar 
    private final JTextArea historyArea;
    private final JLabel    turnCountLabel;

    // Sidebar 
    private final DefaultListModel<SessionMeta> sessionModel = new DefaultListModel<>();
    private final JList<SessionMeta>            sessionList  = new JList<>(sessionModel);

    // Mode selector 
    private final JComboBox<AgentMode> modeSelector = new JComboBox<>(AgentMode.values());

    // State
    private ClinwareAgent agent;
    private String        lastResponse = "";

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "clinware-gui-agent");
        t.setDaemon(true);
        return t;
    });

    //Constructor

    public AgentWindow() {
        super("Clinware Intelligence Agent  v2.0");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1200, 720));

        // Chat pane
        chat = new JTextPane();
        chat.setEditable(false);
        chat.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        chat.setBackground(BG_DARK);
        chat.setForeground(new Color(220, 220, 220));
        chat.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        doc = chat.getStyledDocument();

        Style base = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
        userStyle   = addStyle("user",   base, FG_USER,   true,  false);
        agentStyle  = addStyle("agent",  base, FG_AGENT,  false, false);
        statusStyle = addStyle("status", base, FG_STATUS, false, false);
        toolStyle   = addStyle("tool",   base, FG_TOOL,   false, false);

        JScrollPane chatScroll = new JScrollPane(chat);
        chatScroll.setBorder(BorderFactory.createEmptyBorder());
        chatScroll.getViewport().setBackground(BG_DARK);

        // Input row
        input = new JTextField();
        input.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        input.setBackground(BG_INPUT);
        input.setForeground(Color.WHITE);
        input.setCaretColor(Color.WHITE);
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(65, 65, 65)),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));

        sendBtn = new JButton("Send");
        styleButton(sendBtn, new Color(45, 120, 70));
        input.addActionListener(e -> sendQuery());
        sendBtn.addActionListener(e -> sendQuery());

        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.setBackground(BG_PANEL);
        inputRow.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, DIVIDER),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        inputRow.add(input,   BorderLayout.CENTER);
        inputRow.add(sendBtn, BorderLayout.EAST);

        JPanel leftPane = new JPanel(new BorderLayout());
        leftPane.setBackground(BG_DARK);
        leftPane.add(chatScroll, BorderLayout.CENTER);
        leftPane.add(inputRow,   BorderLayout.SOUTH);

        // Sidebar
        historyArea = new JTextArea();
        historyArea.setEditable(false);
        historyArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        historyArea.setBackground(BG_SIDEBAR);
        historyArea.setForeground(new Color(185, 185, 185));
        historyArea.setLineWrap(true);
        historyArea.setWrapStyleWord(true);
        historyArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JScrollPane turnsScroll = new JScrollPane(historyArea);
        turnsScroll.setBorder(BorderFactory.createEmptyBorder());
        turnsScroll.getViewport().setBackground(BG_SIDEBAR);

        turnCountLabel = new JLabel("  0 turns in session");
        turnCountLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        turnCountLabel.setForeground(FG_STATUS);

        JLabel fileLabel = new JLabel("  " + SessionStore.defaultPath());
        fileLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        fileLabel.setForeground(FG_DIM);

        JPanel turnsInfo = new JPanel(new GridLayout(2, 1, 0, 3));
        turnsInfo.setBackground(BG_PANEL);
        turnsInfo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, DIVIDER),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        turnsInfo.add(turnCountLabel);
        turnsInfo.add(fileLabel);

        JPanel turnsPanel = new JPanel(new BorderLayout());
        turnsPanel.setBackground(BG_SIDEBAR);
        turnsPanel.add(turnsScroll, BorderLayout.CENTER);
        turnsPanel.add(turnsInfo,   BorderLayout.SOUTH);

        // Sidebar 
        sessionList.setBackground(BG_SIDEBAR);
        sessionList.setForeground(new Color(185, 185, 185));
        sessionList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        sessionList.setSelectionBackground(BG_SEL);
        sessionList.setSelectionForeground(Color.WHITE);
        sessionList.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        sessionList.setCellRenderer(new SessionCellRenderer());
        sessionList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) loadSelectedSession();
            }
        });

        JScrollPane sessionsScroll = new JScrollPane(sessionList);
        sessionsScroll.setBorder(BorderFactory.createEmptyBorder());
        sessionsScroll.getViewport().setBackground(BG_SIDEBAR);

        JButton loadBtn = new JButton("Load Selected");
        styleButton(loadBtn, new Color(55, 95, 155));
        loadBtn.addActionListener(e -> loadSelectedSession());

        JPanel loadRow = new JPanel(new BorderLayout());
        loadRow.setBackground(BG_PANEL);
        loadRow.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, DIVIDER),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        loadRow.add(loadBtn, BorderLayout.CENTER);

        JPanel sessionsPanel = new JPanel(new BorderLayout());
        sessionsPanel.setBackground(BG_SIDEBAR);
        sessionsPanel.add(sessionsScroll, BorderLayout.CENTER);
        sessionsPanel.add(loadRow,        BorderLayout.SOUTH);

        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.setBackground(BG_PANEL);
        tabs.setForeground(new Color(180, 180, 180));
        tabs.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        tabs.addTab("  Turns  ",    turnsPanel);
        tabs.addTab("  Sessions  ", sessionsPanel);
        tabs.addChangeListener(e -> { if (tabs.getSelectedIndex() == 1) loadSessionsList(); });

        JPanel sidePane = new JPanel(new BorderLayout());
        sidePane.setBackground(BG_SIDEBAR);
        sidePane.add(tabs, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPane, sidePane);
        split.setDividerLocation(790);
        split.setDividerSize(2);
        split.setBackground(DIVIDER);
        split.setBorder(null);
        split.setResizeWeight(0.72);

        // Toolbar 

        JLabel titleLabel = new JLabel("  Clinware AI");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        titleLabel.setForeground(new Color(160, 210, 255));

        // Mode selector
        modeSelector.setSelectedItem(AgentMode.HYBRID);
        modeSelector.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        modeSelector.setBackground(new Color(48, 48, 60));
        modeSelector.setForeground(new Color(200, 200, 255));
        modeSelector.setFocusable(false);
        modeSelector.setToolTipText(
                "<html><b>Search mode</b><br>" +
                "MCP only — Verge News MCP (assignment baseline)<br>" +
                "Google Search — real-time Google grounding<br>" +
                "Hybrid — both tools, Gemini picks the best</html>");
        modeSelector.addActionListener(e -> onModeSelected());

        JLabel modeLabel = new JLabel("Mode: ");
        modeLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        modeLabel.setForeground(FG_STATUS);

        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 6));
        modePanel.setBackground(BG_PANEL);
        modePanel.add(modeLabel);
        modePanel.add(modeSelector);

        // Right-side buttons
        JButton newChatBtn = new JButton("+ New Chat");
        JButton copyBtn    = new JButton("Copy");
        JButton exportBtn  = new JButton("Export");
        JButton helpBtn    = new JButton("Help");
        JButton saveBtn    = new JButton("Save");
        JButton resetBtn   = new JButton("Reset");

        styleButton(newChatBtn, new Color(40, 130, 70));
        styleButton(copyBtn,    new Color(70,  70,  70));
        styleButton(exportBtn,  new Color(60,  90, 130));
        styleButton(helpBtn,    new Color(75,  75,  75));
        styleButton(saveBtn,    new Color(55,  95, 155));
        styleButton(resetBtn,   new Color(155, 55,  55));

        newChatBtn.addActionListener(e -> onNewChat());
        copyBtn.addActionListener(e    -> onCopyResponse());
        exportBtn.addActionListener(e  -> onExportChat());
        helpBtn.addActionListener(e    -> onHelp());
        saveBtn.addActionListener(e    -> onSave());
        resetBtn.addActionListener(e   -> onReset());

        newChatBtn.setToolTipText("Archive current chat and start fresh");
        copyBtn.setToolTipText("Copy last agent response to clipboard");
        exportBtn.setToolTipText("Export this conversation to a text file");
        resetBtn.setToolTipText("Clear conversation memory (keeps window open)");

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        rightBtns.setBackground(BG_PANEL);
        rightBtns.add(newChatBtn);
        rightBtns.add(copyBtn);
        rightBtns.add(exportBtn);
        rightBtns.add(makeSeparator());
        rightBtns.add(helpBtn);
        rightBtns.add(saveBtn);
        rightBtns.add(resetBtn);

        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBackground(BG_PANEL);
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DIVIDER));
        toolbar.add(titleLabel, BorderLayout.WEST);
        toolbar.add(modePanel,  BorderLayout.CENTER);
        toolbar.add(rightBtns,  BorderLayout.EAST);

        setLayout(new BorderLayout());
        add(toolbar, BorderLayout.NORTH);
        add(split,   BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(null);
    }

    // Wiring 

    public void setAgent(ClinwareAgent a) {
        this.agent = a;
    
        SwingUtilities.invokeLater(() -> {
            modeSelector.setSelectedItem(a.getMode());
        });
    }

    public void showWelcome(boolean sessionRestored) {
        appendRaw("═".repeat(55) + "\n", statusStyle);
        appendRaw("  Clinware Intelligence Agent  v2.0\n", statusStyle);
        appendRaw("═".repeat(55) + "\n\n", statusStyle);
        appendRaw(sessionRestored
                ? "  ✔  Previous session restored.\n"
                : "  ✔  Ready — type your first question below.\n", statusStyle);
        appendRaw("  Commands: /help  /mode  /reset  /save  /clear  exit\n\n", statusStyle);
        refreshHistory();
    }

    public void runDemoQuery(String query) {
        appendRaw("  Demo: \"" + query + "\"\n\n", statusStyle);
        setInputEnabled(false);
        executor.submit(() -> agent.answer(query));
    }

    //  AgentOutput

    @Override public void thinkingFirst() { append("  ✦  Sending query to Gemini...\n", statusStyle); }
    @Override public void thinkingSynth() { append("  ✦  Synthesizing results...\n",    statusStyle); }
    @Override public void warn(String t)  { append("  ⚠  " + t + "\n",                 statusStyle); }
    @Override public void error(String t) { append("  ✗  " + t + "\n",                 statusStyle); }

    @Override
    public void toolCalling(String name, String kw) {
        append("\n   " + name + "(keyword=\"" + kw + "\")\n", toolStyle);
    }

    @Override
    public void toolResult(boolean found, int count) {
        if (found) append("  Retrieved " + count + " result(s)\n", toolStyle);
        else       append("  No articles found\n",                 toolStyle);
    }

    @Override
    public void toolFallback(String kw) {
        append("  ↩  Retrying with: \"" + kw + "\"\n", toolStyle);
    }

    @Override
    public void agentAnswer(String text) {
        String plain = MarkdownRenderer.stripMarkdown(text == null ? "" : text.trim());
        lastResponse = plain;

        Thread tw = new Thread(() -> {
            SwingUtilities.invokeLater(() -> appendRaw("\nAgent › ", agentStyle));
            String[] tokens = plain.split("(?<=\\s)|(?=\\s)");
            for (String token : tokens) {
                SwingUtilities.invokeLater(() -> appendRaw(token, agentStyle));
                if (!token.isBlank()) {
                    try { Thread.sleep(14); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                }
            }
            SwingUtilities.invokeLater(() -> {
                appendRaw("\n\n", agentStyle);
                setInputEnabled(true);
                refreshHistory();
            });
        }, "clinware-typewriter");
        tw.setDaemon(true);
        tw.start();
    }

    //  Input handling

    private void sendQuery() {
        if (agent == null) return;
        String query = input.getText().trim();
        if (query.isBlank()) return;
        input.setText("");

        // Slash commands
        String lower = query.toLowerCase();
        switch (lower) {
            case "/reset"    -> { onReset();        return; }
            case "/save"     -> { onSave();         return; }
            case "/help"     -> { onHelp();         return; }
            case "/clear"    -> { clearChat();      return; }
            case "/new"      -> { onNewChat();      return; }
            case "/copy"     -> { onCopyResponse(); return; }
            case "/export"   -> { onExportChat();   return; }
            case "/sessions" -> { onShowSessions(); return; }
        }
        if (lower.equals("exit") || lower.equals("quit")) { dispose(); return; }

        // /mode [mcp|grounding|hybrid]
        if (lower.startsWith("/mode")) {
            String[] parts = query.split("\\s+", 2);
            if (parts.length > 1) {
                AgentMode m = AgentMode.from(parts[1]);
                agent.setMode(m);
                modeSelector.setSelectedItem(m);
                append("\n  ✦  Mode → " + m.label + "\n\n", statusStyle);
            } else {
                append("\n  Mode: " + agent.getMode().label + "\n"
                     + "  Usage: /mode mcp | /mode grounding | /mode hybrid\n\n", statusStyle);
            }
            return;
        }

        append("\nYou › " + query + "\n", userStyle);
        setInputEnabled(false);
        executor.submit(() -> agent.answer(query));
    }

    // Toolbar actions

    private void onModeSelected() {
        if (agent == null) return;
        AgentMode selected = (AgentMode) modeSelector.getSelectedItem();
        if (selected == null || selected == agent.getMode()) return;
        agent.setMode(selected);
        append("\n  ✦  Mode → " + selected.label + "\n\n", statusStyle);
    }

    private void onNewChat() {
        boolean hadHistory = agent != null && !agent.getHistory().isEmpty();
        if (hadHistory) {
            SessionStore.archiveCurrent(agent.getHistory());
            agent.resetHistory();
        }
        clearChat();
        appendRaw("═".repeat(55) + "\n", statusStyle);
        appendRaw("  New chat started.\n", statusStyle);
        appendRaw("═".repeat(55) + "\n\n", statusStyle);
        if (hadHistory)
            appendRaw("  ✔  Previous session archived to Sessions tab.\n\n", statusStyle);
        appendRaw("  Ask anything — or type /help for commands.\n\n", statusStyle);
        refreshHistory();
        loadSessionsList();
    }

    private void onReset() {
        if (agent != null) agent.resetHistory();
        append("\n  ✔  Conversation memory cleared.\n", statusStyle);
        refreshHistory();
    }

    private void onSave() {
        if (agent == null) return;
        SessionStore.save(agent.getHistory(), SessionStore.defaultPath());
        append("\n  ✔  Session saved → " + SessionStore.defaultPath() + "\n", statusStyle);
    }

    private void onCopyResponse() {
        if (lastResponse.isBlank()) { append("\n  ⚠  No response to copy yet.\n", statusStyle); return; }
        Toolkit.getDefaultToolkit()
               .getSystemClipboard()
               .setContents(new StringSelection(lastResponse), null);
        append("\n  ✔  Last response copied to clipboard.\n", statusStyle);
    }

    private void onExportChat() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Export Conversation");
        fc.setSelectedFile(new java.io.File("clinware_chat.txt"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        Path dest = fc.getSelectedFile().toPath();
        try {
            List<Map.Entry<String, String>> pairs =
                    agent != null ? agent.getHistoryPairs(Integer.MAX_VALUE) : List.of();
            StringBuilder sb = new StringBuilder("Clinware Intelligence Agent — Conversation Export\n");
            sb.append("═".repeat(55)).append("\n\n");
            for (Map.Entry<String, String> p : pairs) {
                String role = "model".equalsIgnoreCase(p.getKey()) ? "Agent" : "You  ";
                sb.append(role).append(" › ").append(p.getValue()).append("\n\n");
            }
            Files.writeString(dest, sb.toString());
            append("\n  ✔  Exported → " + dest + "\n", statusStyle);
        } catch (IOException e) {
            append("\n  ✗  Export failed: " + e.getMessage() + "\n", statusStyle);
        }
    }

    private void onHelp() {
        append(
            "\n  COMMANDS\n" +
            "  ──────────────────────────────────────────────────\n" +
            "  /help           show this guide\n" +
            "  /mode           show current mode\n" +
            "  /mode mcp       News MCP only\n" +
            "  /mode grounding Normal Search grounding only\n" +
            "  /mode hybrid    both MCP + Google Search (default)\n" +
            "  /new            archive current chat and start fresh\n" +
            "  /reset          clear memory (no archive)\n" +
            "  /save           flush session to disk\n" +
            "  /copy           copy last response to clipboard\n" +
            "  /export         save conversation to a .txt file\n" +
            "  /sessions       show all archived sessions\n" +
            "  /clear          clear this chat window\n" +
            "  exit            close window (session auto-saved)\n\n" +
            "  WHAT YOU CAN ASK\n" +
            "  ──────────────────────────────────────────────────\n" +
            "  • Clinware — products, funding, news, partners\n" +
            "  • Disease research — mechanisms, drug pipeline\n" +
            "  • Healthcare market — AI, M&A, funding rounds\n\n",
            statusStyle);
    }

    private void onShowSessions() {
        loadSessionsList();
        append("\n  ℹ  Switch to the 'Sessions' tab in the sidebar.\n", statusStyle);
    }

    // Session loading

    private void clearChat() {
        Runnable r = () -> {
            try { doc.remove(0, doc.getLength()); }
            catch (BadLocationException ignored) {}
        };
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeLater(r);
    }

    private void loadSessionsList() {
        executor.submit(() -> {
            List<SessionMeta> sessions = SessionStore.listSessions();
            SwingUtilities.invokeLater(() -> {
                sessionModel.clear();
                sessions.forEach(sessionModel::addElement);
            });
        });
    }

    private void loadSelectedSession() {
        SessionMeta selected = sessionList.getSelectedValue();
        if (selected == null) {
            append("\n  ⚠  Select a session from the list first.\n", statusStyle);
            return;
        }
        executor.submit(() -> {
            List<com.google.genai.types.Content> loaded = SessionStore.load(selected.path);
            SwingUtilities.invokeLater(() -> {
                if (agent != null && !agent.getHistory().isEmpty())
                    SessionStore.archiveCurrent(agent.getHistory());

                if (agent != null) agent.setHistory(loaded);

                List<Map.Entry<String, String>> pairs =
                        agent != null ? agent.getHistoryPairs(Integer.MAX_VALUE) : List.of();

                clearChat();
                appendRaw("═".repeat(55) + "\n", statusStyle);
                appendRaw("  Loaded: " + selected.label + "\n", statusStyle);
                appendRaw("═".repeat(55) + "\n\n", statusStyle);

                if (pairs.isEmpty()) {
                    appendRaw("  (session is empty)\n\n", statusStyle);
                } else {
                    int shown   = Math.min(pairs.size(), 20);
                    int skipped = pairs.size() - shown;
                    if (skipped > 0)
                        appendRaw("  … " + skipped + " earlier turns not shown …\n\n", statusStyle);

                    for (int i = pairs.size() - shown; i < pairs.size(); i++) {
                        Map.Entry<String, String> p = pairs.get(i);
                        boolean isUser = !"model".equalsIgnoreCase(p.getKey());
                        appendRaw(isUser ? "You   › " : "Agent › ", isUser ? userStyle : agentStyle);
                        appendRaw(p.getValue() + "\n\n",              isUser ? userStyle : agentStyle);
                    }
                }
                appendRaw("  ───────────────────────────────────────────────\n", statusStyle);
                appendRaw("  Session restored — continue below.\n\n",            statusStyle);

                refreshHistory();
                loadSessionsList();
            });
        });
    }

    private void refreshHistory() {
        if (agent == null) return;
        List<Map.Entry<String, String>> pairs = agent.getHistoryPairs(90);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pairs.size(); i++) {
            Map.Entry<String, String> p = pairs.get(i);
            String role = "model".equalsIgnoreCase(p.getKey()) ? "Agent" : "You  ";
            sb.append("[").append(i + 1).append("] ")
              .append(role).append(" › ")
              .append(p.getValue())
              .append("\n\n");
        }
        historyArea.setText(sb.toString());
        historyArea.setCaretPosition(0);
        turnCountLabel.setText("  " + pairs.size() + " turns in session");
    }

    // Low-level helpers 

    private void append(String text, Style style) {
        SwingUtilities.invokeLater(() -> appendRaw(text, style));
    }

    private void appendRaw(String text, Style style) {
        try {
            doc.insertString(doc.getLength(), text, style);
            chat.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {}
    }

    private void setInputEnabled(boolean on) {
        SwingUtilities.invokeLater(() -> {
            input.setEnabled(on);
            sendBtn.setEnabled(on);
            if (on) input.requestFocusInWindow();
        });
    }

    private Style addStyle(String name, Style base, Color fg, boolean bold, boolean italic) {
        Style s = doc.addStyle(name, base);
        StyleConstants.setForeground(s, fg);
        if (bold)   StyleConstants.setBold(s, true);
        if (italic) StyleConstants.setItalic(s, true);
        return s;
    }

    private static void styleButton(JButton btn, Color bg) {
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(bg.darker()),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)));
        btn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
    }

    private static JComponent makeSeparator() {
        JPanel sep = new JPanel();
        sep.setBackground(DIVIDER);
        sep.setPreferredSize(new Dimension(1, 20));
        return sep;
    }

    // Session cell renderer

    private static final class SessionCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean selected, boolean focused) {
            JLabel lbl = (JLabel) super.getListCellRendererComponent(
                    list, value, index, selected, focused);
            if (value instanceof SessionMeta m) {
                lbl.setText("<html>"
                        + "<b style='color:#aad4ff'>" + m.label + "</b><br/>"
                        + "<span style='color:#888; font-size:10px'>" + m.preview + "</span>"
                        + "</html>");
            }
            lbl.setBackground(selected ? BG_SEL : BG_SIDEBAR);
            lbl.setOpaque(true);
            lbl.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(48, 48, 48)),
                    BorderFactory.createEmptyBorder(7, 10, 7, 10)));
            return lbl;
        }
    }
}
