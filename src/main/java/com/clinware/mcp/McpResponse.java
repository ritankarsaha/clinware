package com.clinware.mcp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;


public class McpResponse {

    private String      jsonrpc;
    private JsonElement id;
    private JsonElement result;
    private JsonObject  error;

    // Accessors 

    public String      getJsonrpc() { return jsonrpc; }
    public JsonElement getId()      { return id; }
    public JsonElement getResult()  { return result; }
    public JsonObject  getError()   { return error; }


    public boolean isError() {
        return error != null;
    }


    public String getErrorMessage() {
        if (error == null) return "Unknown error";
        JsonElement msg = error.get("message");
        return (msg != null) ? msg.getAsString() : error.toString();
    }

    @Override
    public String toString() {
        return "McpResponse{jsonrpc='" + jsonrpc + "', id=" + id
             + ", result=" + result + ", error=" + error + '}';
    }
}
