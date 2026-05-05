package com.openmanus.aiframework.runtime.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

public record McpInvokeRequest(
        String toolName,
        JsonNode arguments
) {

    public McpInvokeRequest {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("mcp invoke toolName cannot be blank");
        }
        arguments = arguments == null ? JsonNodeFactory.instance.objectNode() : arguments;
    }
}
