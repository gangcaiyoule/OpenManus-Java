package com.openmanus.aiframework.tool.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openmanus.aiframework.runtime.mcp.McpToolDefinition;
import com.openmanus.aiframework.runtime.model.AiToolSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolSpecAdapterTest {

    private final McpToolSpecAdapter adapter = new McpToolSpecAdapter();

    @Test
    void shouldMapToolDefinitionToRuntimeSpec() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("query").put("type", "string");
        schema.putArray("required").add("query");

        AiToolSpec spec = adapter.toAiToolSpec(new McpToolDefinition("mcp.search", "search docs", schema));

        assertEquals("mcp.search", spec.name());
        assertEquals("search docs", spec.description());
        assertEquals("object", spec.inputSchema().path("type").asText());
        assertTrue(spec.inputSchema().path("properties").has("query"));
        assertEquals("string", spec.inputSchema().path("properties").path("query").path("type").asText());
        assertEquals(1, spec.inputSchema().path("required").size());
    }

    @Test
    void shouldNormalizeInvalidSchemaToObjectShape() {
        JsonNode invalidSchema = JsonNodeFactory.instance.textNode("invalid");

        AiToolSpec spec = adapter.toAiToolSpec(new McpToolDefinition("mcp.invalid", "invalid", invalidSchema));

        assertEquals("object", spec.inputSchema().path("type").asText());
        assertTrue(spec.inputSchema().path("properties").isObject());
        assertTrue(spec.inputSchema().path("required").isArray());
    }

    @Test
    void shouldDowngradeNonObjectTypeSchemaToDefaultObjectShape() {
        ObjectNode nonObjectTypeSchema = JsonNodeFactory.instance.objectNode();
        nonObjectTypeSchema.put("type", "array");
        nonObjectTypeSchema.putArray("items").addObject().put("type", "string");

        AiToolSpec spec = adapter.toAiToolSpec(
                new McpToolDefinition("mcp.array", "array schema", nonObjectTypeSchema)
        );

        assertEquals("object", spec.inputSchema().path("type").asText());
        assertTrue(spec.inputSchema().path("properties").isObject());
        assertTrue(spec.inputSchema().path("required").isArray());
        assertFalse(spec.inputSchema().has("items"));
    }

    @Test
    void shouldDefensivelyCopySchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.putObject("properties");
        schema.putArray("required");
        McpToolDefinition definition = new McpToolDefinition("mcp.echo", "echo", schema);

        AiToolSpec first = adapter.toAiToolSpec(definition);
        ((ObjectNode) first.inputSchema()).put("mutatedByCaller", true);

        AiToolSpec second = adapter.toAiToolSpec(definition);
        assertFalse(second.inputSchema().has("mutatedByCaller"));
    }

    @Test
    void shouldRejectNullDefinition() {
        assertThrows(NullPointerException.class, () -> adapter.toAiToolSpec(null));
    }
}
