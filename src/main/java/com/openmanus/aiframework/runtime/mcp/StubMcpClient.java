package com.openmanus.aiframework.runtime.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class StubMcpClient implements McpClient {

    private final List<McpToolDefinition> tools;
    private final Map<String, StubToolHandler> handlers;
    private final List<McpResourceDefinition> resources;
    private final Map<String, McpResourceReadResult> resourceResults;

    public StubMcpClient() {
        LinkedHashMap<String, StubToolHandler> toolHandlers = new LinkedHashMap<>();
        toolHandlers.put("mcp.echo", this::handleEcho);
        toolHandlers.put("mcp.sum", this::handleSum);
        this.handlers = Map.copyOf(toolHandlers);
        this.tools = List.of(
                new McpToolDefinition("mcp.echo", "Echo text input.", echoSchema()),
                new McpToolDefinition("mcp.sum", "Sum integer array input.", sumSchema())
        );
        this.resources = List.of(
                new McpResourceDefinition(
                        "mcp://resource/readme",
                        "mcp.readme",
                        "Project readme summary resource.",
                        "text/markdown"
                )
        );
        this.resourceResults = Map.of(
                "mcp://resource/readme",
                new McpResourceReadResult(
                        "mcp://resource/readme",
                        "text/markdown",
                        "OpenManus MCP stub resource",
                        false
                )
        );
    }

    @Override
    public List<McpToolDefinition> discoverTools() {
        return tools.stream()
                .map(this::copyToolDefinition)
                .toList();
    }

    @Override
    public McpToolResult invoke(McpInvokeRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        StubToolHandler handler = handlers.get(request.toolName());
        if (handler == null) {
            throw new IllegalArgumentException("unknown MCP tool: " + request.toolName());
        }
        return handler.handle(request.arguments());
    }

    @Override
    public List<McpResourceDefinition> discoverResources() {
        return resources.stream()
                .map(this::copyResourceDefinition)
                .toList();
    }

    @Override
    public McpResourceReadResult readResource(String resourceUri) {
        if (resourceUri == null || resourceUri.isBlank()) {
            throw new IllegalArgumentException("mcp resource uri cannot be blank");
        }
        McpResourceReadResult result = resourceResults.get(resourceUri.trim());
        if (result == null) {
            throw new IllegalArgumentException("unknown MCP resource: " + resourceUri);
        }
        return new McpResourceReadResult(
                result.uri(),
                result.mimeType(),
                result.content(),
                result.error()
        );
    }

    private McpToolResult handleEcho(JsonNode arguments) {
        String text = arguments.path("text").asText("");
        return new McpToolResult("mcp.echo", text, false);
    }

    private McpToolResult handleSum(JsonNode arguments) {
        JsonNode valuesNode = arguments.path("values");
        if (!valuesNode.isArray()) {
            return new McpToolResult("mcp.sum", "values must be an array", true);
        }
        int sum = 0;
        ArrayNode values = (ArrayNode) valuesNode;
        for (JsonNode value : values) {
            sum += value.asInt(0);
        }
        return new McpToolResult("mcp.sum", Integer.toString(sum), false);
    }

    private static JsonNode echoSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("text").put("type", "string");
        schema.putArray("required").add("text");
        return schema;
    }

    private static JsonNode sumSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ObjectNode values = properties.putObject("values");
        values.put("type", "array");
        values.putObject("items").put("type", "integer");
        schema.putArray("required").add("values");
        return schema;
    }

    private McpToolDefinition copyToolDefinition(McpToolDefinition definition) {
        return new McpToolDefinition(
                definition.name(),
                definition.description(),
                definition.inputSchema().deepCopy()
        );
    }

    private McpResourceDefinition copyResourceDefinition(McpResourceDefinition definition) {
        return new McpResourceDefinition(
                definition.uri(),
                definition.name(),
                definition.description(),
                definition.mimeType()
        );
    }

    @FunctionalInterface
    private interface StubToolHandler {
        McpToolResult handle(JsonNode arguments);
    }
}
