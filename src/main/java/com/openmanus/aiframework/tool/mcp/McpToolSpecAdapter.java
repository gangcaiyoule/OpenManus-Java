package com.openmanus.aiframework.tool.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openmanus.aiframework.runtime.mcp.McpToolDefinition;
import com.openmanus.aiframework.runtime.model.AiToolSpec;

import java.util.Objects;

/**
 * Converts MCP tool definitions into the framework's runtime tool specification.
 */
public final class McpToolSpecAdapter {

    public AiToolSpec toAiToolSpec(McpToolDefinition definition) {
        Objects.requireNonNull(definition, "definition cannot be null");
        ObjectNode schema = sanitizeSchema(definition.inputSchema());
        return new AiToolSpec(definition.name(), definition.description(), schema);
    }

    private static ObjectNode sanitizeSchema(JsonNode inputSchema) {
        if (!(inputSchema instanceof ObjectNode objectNode)) {
            return defaultObjectSchema();
        }

        String type = objectNode.path("type").asText("");
        if (!"object".equalsIgnoreCase(type)) {
            return defaultObjectSchema();
        }

        ObjectNode schema = objectNode.deepCopy();
        schema.put("type", "object");
        if (!(schema.path("properties") instanceof ObjectNode)) {
            schema.set("properties", JsonNodeFactory.instance.objectNode());
        }
        if (!(schema.path("required") instanceof ArrayNode)) {
            schema.set("required", JsonNodeFactory.instance.arrayNode());
        }
        return schema;
    }

    private static ObjectNode defaultObjectSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.set("properties", JsonNodeFactory.instance.objectNode());
        schema.set("required", JsonNodeFactory.instance.arrayNode());
        return schema;
    }
}
