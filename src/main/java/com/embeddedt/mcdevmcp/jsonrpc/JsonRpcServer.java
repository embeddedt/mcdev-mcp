package com.embeddedt.mcdevmcp.jsonrpc;

import dev.nolij.zson.Zson;
import dev.nolij.zson.ZsonValue;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class JsonRpcServer {
    private final List<ToolDefinition> tools;
    private final String instructions;
    private final String name;
    private final String version;

    public JsonRpcServer(List<ToolDefinition> tools, String instructions, String name, String version) {
        this.tools = tools;
        this.instructions = instructions;
        this.name = name;
        this.version = version;
    }

    public void run() throws IOException {
        var reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        var writer = new OutputStreamWriter(System.out, StandardCharsets.UTF_8);

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) continue;
            try {
                Map<String, ZsonValue> request = Zson.parseString(line);
                handleRequest(request, writer);
            } catch (Exception e) {
                System.err.println("Failed to handle request: " + e.getMessage());
            }
        }
    }

    private void handleRequest(Map<String, ZsonValue> request, OutputStreamWriter writer) throws IOException {
        ZsonValue idEntry = request.get("id");

        // Notification (no id or null id) — no response
        if (idEntry == null || idEntry.value == null) {
            return;
        }
        Object id = idEntry.value;

        String method = getString(request, "method");

        try {
            Map<String, ZsonValue> result = dispatch(method, getMap(request, "params"));
            sendResponse(writer, id, result, null);
        } catch (JsonRpcException e) {
            Map<String, ZsonValue> error = Zson.object(
                    Zson.entry("code", e.code),
                    Zson.entry("message", e.getMessage())
            );
            sendResponse(writer, id, null, error);
        } catch (Exception e) {
            Map<String, ZsonValue> error = Zson.object(
                    Zson.entry("code", -32603),
                    Zson.entry("message", e.getMessage() != null ? e.getMessage() : "Internal error")
            );
            sendResponse(writer, id, null, error);
        }
    }

    private Map<String, ZsonValue> dispatch(String method, Map<String, ZsonValue> params) throws Exception {
        if (method == null) {
            throw new JsonRpcException(-32600, "Invalid request: missing method");
        }
        return switch (method) {
            case "initialize" -> handleInitialize();
            case "tools/list" -> handleToolsList();
            case "tools/call" -> handleToolsCall(params);
            case "ping" -> Zson.object();
            default -> throw new JsonRpcException(-32601, "Method not found: " + method);
        };
    }

    private Map<String, ZsonValue> handleInitialize() {
        return Zson.object(
                Zson.entry("protocolVersion", "2024-11-05"),
                Zson.entry("capabilities", Zson.object(
                        Zson.entry("tools", Zson.object())
                )),
                Zson.entry("serverInfo", Zson.object(
                        Zson.entry("name", name),
                        Zson.entry("version", version)
                )),
                Zson.entry("instructions", instructions)
        );
    }

    private Map<String, ZsonValue> handleToolsList() {
        List<Object> toolsArray = new ArrayList<>();
        for (ToolDefinition tool : tools) {
            toolsArray.add(Zson.object(
                    Zson.entry("name", tool.name()),
                    Zson.entry("description", tool.description()),
                    Zson.entry("inputSchema", tool.inputSchema())
            ));
        }
        return Zson.object(Zson.entry("tools", toolsArray));
    }

    @SuppressWarnings("unchecked")
    private Map<String, ZsonValue> handleToolsCall(Map<String, ZsonValue> params) throws Exception {
        if (params == null) {
            throw new IllegalArgumentException("Missing params for tools/call");
        }
        String toolName = getString(params, "name");
        if (toolName == null) {
            throw new IllegalArgumentException("Missing tool name in tools/call params");
        }

        ZsonValue argsEntry = params.get("arguments");
        Map<String, ZsonValue> arguments = (argsEntry != null && argsEntry.value instanceof Map<?, ?> m)
                ? (Map<String, ZsonValue>) m
                : Zson.object();

        ToolDefinition tool = tools.stream()
                .filter(t -> t.name().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + toolName));

        ToolCallResult toolResult;
        try {
            toolResult = tool.handler().apply(arguments).get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException(cause.getMessage(), cause);
        }

        List<Object> contentArray = new ArrayList<>();
        for (ToolCallResult.ContentItem item : toolResult.content()) {
            contentArray.add(Zson.object(
                    Zson.entry("type", item.type()),
                    Zson.entry("text", item.text())
            ));
        }
        return Zson.object(
                Zson.entry("isError", toolResult.isError()),
                Zson.entry("content", contentArray)
        );
    }

    private synchronized void sendResponse(OutputStreamWriter writer, Object id,
                                           Map<String, ZsonValue> result,
                                           Map<String, ZsonValue> error) throws IOException {
        Map<String, ZsonValue> response = Zson.object(
                Zson.entry("jsonrpc", "2.0"),
                Zson.entry("id", id)
        );
        if (error != null) {
            response.put("error", new ZsonValue(error));
        } else {
            response.put("result", new ZsonValue(result));
        }
        // Compact single-line output for newline-delimited JSON-RPC
        String json = new Zson().withIndent("").stringify(response).replace("\n", "");
        writer.write(json);
        writer.write("\n");
        writer.flush();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String getString(Map<String, ZsonValue> map, String key) {
        ZsonValue v = map.get(key);
        return (v != null && v.value instanceof String s) ? s : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ZsonValue> getMap(Map<String, ZsonValue> map, String key) {
        ZsonValue v = map.get(key);
        return (v != null && v.value instanceof Map<?, ?> m) ? (Map<String, ZsonValue>) m : null;
    }

    // ── Exceptions ───────────────────────────────────────────────────────────

    private static class JsonRpcException extends RuntimeException {
        final int code;

        JsonRpcException(int code, String message) {
            super(message);
            this.code = code;
        }
    }
}
