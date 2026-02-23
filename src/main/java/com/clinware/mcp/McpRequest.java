package com.clinware.mcp;

import java.util.Map;


public class McpRequest {

    private final String jsonrpc = "2.0";
    private final Object id;   
    private final String method;
    private final Object params;

    /** Standard request */
    public McpRequest(int id, String method, Object params) {
        this.id     = id;
        this.method = method;
        this.params = params;
    }

    /** Notification */
    public McpRequest(String method, Object params) {
        this.id     = null;
        this.method = method;
        this.params = params;
    }

    // Factory helpers

    /** Build the MCP initialize request. */
    public static McpRequest initialize(int id) {
        return new McpRequest(id, "initialize", Map.of(
            "protocolVersion", "2024-11-05",
            "capabilities",    Map.of(),
            "clientInfo",      Map.of("name", "clinware-agent", "version", "1.0.0")
        ));
    }

    /** Build the initialized notification  */
    public static McpRequest initializedNotification() {
        return new McpRequest("notifications/initialized", Map.of());
    }

    /** Build a call request. */
    public static McpRequest toolsCall(int id, String toolName, Map<String, Object> arguments) {
        return new McpRequest(id, "tools/call", Map.of(
            "name",      toolName,
            "arguments", arguments
        ));
    }

    /** Build a shutdown request. */
    public static McpRequest shutdown(int id) {
        return new McpRequest(id, "shutdown", null);
    }

    // Getters (used by Gson) 

    public String getJsonrpc() { return jsonrpc; }
    public Object getId()      { return id; }
    public String getMethod()  { return method; }
    public Object getParams()  { return params; }
}
