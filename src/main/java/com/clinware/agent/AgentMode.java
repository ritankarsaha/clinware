package com.clinware.agent;

public enum AgentMode {

    MCP("Only MCP Based News Search"),
    GROUNDING("CLinware Search"),
    HYBRID("Hybrid Search");

    public final String label;

    AgentMode(String label) { this.label = label; }

    public static AgentMode from(String s) {
        if (s == null) return HYBRID;
        return switch (s.trim().toLowerCase()) {
            case "mcp"       -> MCP;
            case "grounding" -> GROUNDING;
            default          -> HYBRID;
        };
    }

    @Override
    public String toString() { return label; }
}
