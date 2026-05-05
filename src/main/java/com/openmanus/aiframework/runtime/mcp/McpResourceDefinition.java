package com.openmanus.aiframework.runtime.mcp;

public record McpResourceDefinition(
        String uri,
        String name,
        String description,
        String mimeType
) {

    public McpResourceDefinition {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("mcp resource uri cannot be blank");
        }
        name = name == null ? "" : name;
        description = description == null ? "" : description;
        mimeType = mimeType == null ? "" : mimeType;
    }
}
