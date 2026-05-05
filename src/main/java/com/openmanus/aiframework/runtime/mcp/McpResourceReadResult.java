package com.openmanus.aiframework.runtime.mcp;

public record McpResourceReadResult(
        String uri,
        String mimeType,
        String content,
        boolean error
) {

    public McpResourceReadResult {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("mcp resource result uri cannot be blank");
        }
        mimeType = mimeType == null ? "" : mimeType;
        content = content == null ? "" : content;
    }
}
