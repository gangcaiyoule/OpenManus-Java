package com.openmanus.aiframework.runtime.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public record McpToolDefinition(
        String name,
        String description,
        JsonNode inputSchema
) {

    public McpToolDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("mcp tool name cannot be blank");
        }
        description = description == null ? "" : description;
        inputSchema = inputSchema == null ? defaultSchema() : inputSchema;
    }

    private static JsonNode defaultSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.putObject("properties");
        return schema;
    }
}
