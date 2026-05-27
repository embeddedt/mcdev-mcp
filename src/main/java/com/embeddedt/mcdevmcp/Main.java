package com.embeddedt.mcdevmcp;

import com.embeddedt.mcdevmcp.external.GetMinecraftSourcesTool;
import com.embeddedt.mcdevmcp.jsonrpc.JsonRpcServer;
import com.embeddedt.mcdevmcp.jsonrpc.ToolDefinition;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class Main {
    private static List<ToolDefinition> buildTools() {
        return List.of(
                GetMinecraftSourcesTool.tool()
        );
    }

    public static void main(String[] args) throws Exception {
        Files.createDirectories(Constants.CACHE_PATH);

        String instructions;
        try (var stream = Main.class.getResourceAsStream("/system-instructions.md")) {
            if (stream == null) {
                throw new IllegalStateException("System instructions missing from resources");
            }
            instructions = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        System.err.println("Initialized");
        new JsonRpcServer(buildTools(), instructions, "mcdev-mcp", Constants.VERSION).run();
    }
}
