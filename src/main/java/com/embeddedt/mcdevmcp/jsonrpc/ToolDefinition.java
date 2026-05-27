package com.embeddedt.mcdevmcp.jsonrpc;

import dev.nolij.zson.ZsonValue;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public record ToolDefinition(
        String name,
        String description,
        Map<String, ZsonValue> inputSchema,
        Function<Map<String, ZsonValue>, CompletableFuture<ToolCallResult>> handler
) {}
