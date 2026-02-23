package com.clinware.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class McpStdioClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(McpStdioClient.class);

    private final long              timeoutMs;
    private final Gson              gson;
    private final AtomicInteger     idCounter = new AtomicInteger(1);
    private final ExecutorService   readerPool;

    private Process       process;
    private BufferedWriter stdin;
    private BufferedReader stdout;

    public McpStdioClient(long timeoutMs) {
        this.timeoutMs  = timeoutMs;
        this.gson       = new GsonBuilder().serializeNulls().create();
        this.readerPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "mcp-reader");
            t.setDaemon(true);
            return t;
        });
    }


    public void start() throws IOException {
        log.info("Starting MCP server process...");

        ProcessBuilder pb = new ProcessBuilder("node",
                System.getenv().getOrDefault("MCP_SERVER_PATH",
                        System.getProperty("user.home") + "/coding/verge-news-mcp/build/index.js"));
        pb.environment().put("NODE_NO_WARNINGS", "1");
        // Redirect stderr to a background logging thread
        pb.redirectErrorStream(false);

        process = pb.start();

        stdin  = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));

        // Drain stderr in background to prevent pipe-buffer blocking
        startStderrLogger(process.getErrorStream());

        log.info("MCP process started. Performing handshake...");
        performHandshake();
        log.info("MCP handshake complete. Client is ready.");
    }

    private void startStderrLogger(InputStream stderr) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(stderr))) {
                String line;
                while ((line = r.readLine()) != null) {
                    log.debug("[MCP stderr] {}", line);
                }
            } catch (IOException ignored) {  }
        }, "mcp-stderr-logger");
        t.setDaemon(true);
        t.start();
    }

    private void performHandshake() throws IOException {
        int initId = idCounter.getAndIncrement();

        //  send initialize
        McpRequest initReq = McpRequest.initialize(initId);
        sendRequest(initReq);

        McpResponse initResp = readResponseWithTimeout();
        if (initResp == null) {
            throw new IOException("MCP handshake timed out waiting for initialize response");
        }
        if (initResp.isError()) {
            throw new IOException("MCP initialize failed: " + initResp.getErrorMessage());
        }
        log.debug("MCP capabilities received: {}", initResp.getResult());

        // send initialized notification 
        sendRequest(McpRequest.initializedNotification());
    }

    // Tool Invocation

    /**
     * Calls an MCP tool and returns the result as a JsonElement.
     *
     * @param toolName  
     * @param args    
     * @return        
     * @throws IOException         
     * @throws TimeoutException    
     * @throws McpToolException     
     */
    public JsonElement callTool(String toolName, Map<String, Object> args)
            throws IOException, TimeoutException, McpToolException {

        int id = idCounter.getAndIncrement();
        McpRequest req = McpRequest.toolsCall(id, toolName, args);

        log.debug("Calling MCP tool '{}' with args: {}", toolName, args);
        sendRequest(req);

        McpResponse resp = readResponseWithTimeout();
        if (resp == null) {
            throw new TimeoutException("MCP tool '" + toolName + "' timed out after " + timeoutMs + "ms");
        }
        if (resp.isError()) {
            throw new McpToolException("MCP tool '" + toolName + "' returned error: " + resp.getErrorMessage());
        }

        log.debug("MCP tool '{}' returned: {}", toolName, resp.getResult());
        return resp.getResult();
    }

    // Shutdown

    @Override
    public void close() {
        log.info("Shutting down MCP client...");
        try {
            if (process != null && process.isAlive()) {
                
                try {
                    int shutId = idCounter.getAndIncrement();
                    sendRequest(McpRequest.shutdown(shutId));
                } catch (IOException ignored) {  }
                process.waitFor(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (process != null) process.destroyForcibly();
            readerPool.shutdownNow();
        }
        log.info("MCP client shut down.");
    }

    // I/O Helpers

    private void sendRequest(McpRequest req) throws IOException {
        String json = gson.toJson(req);
        log.debug("→ MCP: {}", json);
        stdin.write(json);
        stdin.newLine();
        stdin.flush();
    }

    private McpResponse readResponseWithTimeout() {
        Future<McpResponse> future = readerPool.submit(() -> {
            
            String line;
            while ((line = stdout.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("{")) {
                    log.debug("← MCP: {}", line);
                    return gson.fromJson(line, McpResponse.class);
                }
                log.debug("[MCP skip] {}", line);
            }
            return null;
        });

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("MCP response timed out after {}ms", timeoutMs);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            log.error("Error reading MCP response", e.getCause());
            return null;
        }
    }

    //  Custom Exceptions

    public static class McpToolException extends Exception {
        public McpToolException(String message) { super(message); }
    }
}
