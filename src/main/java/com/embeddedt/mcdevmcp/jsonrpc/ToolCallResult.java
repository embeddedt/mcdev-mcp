package com.embeddedt.mcdevmcp.jsonrpc;

import java.util.List;

public record ToolCallResult(List<ContentItem> content, boolean isError) {
    public record ContentItem(String type, String text) {}

    public static ToolCallResult text(String text) {
        return new ToolCallResult(List.of(new ContentItem("text", text)), false);
    }

    public static ToolCallResult error(String text) {
        return new ToolCallResult(List.of(new ContentItem("text", text)), true);
    }
}
