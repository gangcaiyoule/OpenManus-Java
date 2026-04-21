package com.openmanus.aiframework.runtime.mcp;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpClientContractTest {

    private final McpClient client = new StubMcpClient();

    @Test
    void shouldDiscoverStableToolDefinitions() {
        List<McpToolDefinition> tools = client.discoverTools();

        assertEquals(2, tools.size());
        assertEquals("mcp.echo", tools.get(0).name());
        assertEquals("mcp.sum", tools.get(1).name());
        assertEquals("object", tools.get(0).inputSchema().path("type").asText());
        assertTrue(tools.get(0).inputSchema().path("required").isArray());
        assertThrows(UnsupportedOperationException.class,
                () -> tools.add(new McpToolDefinition("x", "y", null)));
    }

    @Test
    void shouldNotLeakMutableSchemaReferenceAcrossDiscoverCalls() {
        List<McpToolDefinition> firstDiscover = client.discoverTools();
        ObjectNode firstSchema = (ObjectNode) firstDiscover.get(0).inputSchema();
        firstSchema.put("mutatedByCaller", true);

        List<McpToolDefinition> secondDiscover = client.discoverTools();
        assertTrue(secondDiscover.get(0).inputSchema().path("mutatedByCaller").isMissingNode());
    }

    @Test
    void shouldInvokeKnownTool() {
        ObjectNode arguments = JsonNodeFactory.instance.objectNode();
        arguments.put("text", "hello mcp");

        McpToolResult result = client.invoke(new McpInvokeRequest("mcp.echo", arguments));

        assertEquals("mcp.echo", result.toolName());
        assertEquals("hello mcp", result.content());
        assertFalse(result.error());
    }

    @Test
    void shouldRejectBlankToolName() {
        assertThrows(IllegalArgumentException.class, () -> new McpInvokeRequest(" ", null));
    }

    @Test
    void shouldFailOnUnknownTool() {
        assertThrows(IllegalArgumentException.class,
                () -> client.invoke(new McpInvokeRequest("mcp.not_found", JsonNodeFactory.instance.objectNode())));
    }

    @Test
    void shouldRejectNullInvokeRequest() {
        assertThrows(NullPointerException.class, () -> client.invoke(null));
    }

    @Test
    void shouldDiscoverStableResourceDefinitions() {
        List<McpResourceDefinition> resources = client.discoverResources();

        assertEquals(1, resources.size());
        assertEquals("mcp://resource/readme", resources.get(0).uri());
        assertEquals("text/markdown", resources.get(0).mimeType());
        assertThrows(UnsupportedOperationException.class,
                () -> resources.add(new McpResourceDefinition("mcp://resource/x", "x", "", "text/plain")));
    }

    @Test
    void shouldNotLeakMutableResourceDefinitionAcrossDiscoverCalls() {
        List<McpResourceDefinition> firstDiscover = client.discoverResources();
        List<McpResourceDefinition> secondDiscover = client.discoverResources();

        assertNotSame(firstDiscover, secondDiscover);
        assertEquals("mcp.readme", secondDiscover.get(0).name());
    }

    @Test
    void shouldReadKnownResource() {
        McpResourceReadResult result = client.readResource("mcp://resource/readme");

        assertEquals("mcp://resource/readme", result.uri());
        assertEquals("text/markdown", result.mimeType());
        assertTrue(result.content().contains("OpenManus MCP stub resource"));
        assertFalse(result.error());
    }

    @Test
    void shouldRejectUnknownResourceUri() {
        assertThrows(IllegalArgumentException.class, () -> client.readResource("mcp://resource/unknown"));
    }
}
