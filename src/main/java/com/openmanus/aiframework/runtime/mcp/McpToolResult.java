package com.openmanus.aiframework.runtime.mcp;

public record McpToolResult(
        String toolName,
        String content,
        boolean error
) {

    public McpToolResult {
        if (toolName == null) {
            throw new IllegalArgumentException("mcp result toolName cannot be null");
        }
        toolName = toolName.trim();
        content = content == null ? "" : content;
    }
}
