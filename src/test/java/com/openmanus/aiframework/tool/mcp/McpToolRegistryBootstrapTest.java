package com.openmanus.aiframework.tool.mcp;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openmanus.aiframework.runtime.mcp.McpClient;
import com.openmanus.aiframework.runtime.mcp.McpInvokeRequest;
import com.openmanus.aiframework.runtime.mcp.McpResourceDefinition;
import com.openmanus.aiframework.runtime.mcp.McpToolDefinition;
import com.openmanus.aiframework.runtime.mcp.McpToolResult;
import com.openmanus.aiframework.runtime.model.AiAgentParameterSchema;
import com.openmanus.aiframework.runtime.model.AiToolSpec;
import com.openmanus.aiframework.tool.AiRegisteredTool;
import com.openmanus.aiframework.tool.AiToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolRegistryBootstrapTest {

    @Test
    void shouldDiscoverAndRegisterMcpToolsIntoRuntimeRegistryShape() {
        McpClient client = new FakeMcpClient(List.of(
                new McpToolDefinition("mcp.echo", "echo", textSchema("text")),
                new McpToolDefinition("mcp.search", "search", textSchema("query"))
        ));
        McpToolRegistryBootstrap bootstrap = new McpToolRegistryBootstrap(client, new McpToolSpecAdapter());
        Map<String, AiRegisteredTool> registry = new LinkedHashMap<>();

        List<AiRegisteredTool> registered = bootstrap.discoverAndRegister(registry);

        assertEquals(2, registered.size());
        assertEquals(2, registry.size());
        assertTrue(registry.containsKey("mcp.echo"));
        assertTrue(registry.containsKey("mcp.search"));
        assertEquals(2, AiToolRegistry.toRuntimeToolSpecifications(registered).size());
    }

    @Test
    void shouldReturnEmptyWhenDiscoverResultIsEmpty() {
        McpToolRegistryBootstrap bootstrap = new McpToolRegistryBootstrap(
                new FakeMcpClient(List.of()),
                new McpToolSpecAdapter()
        );

        List<AiRegisteredTool> registered = bootstrap.discoverAndRegister(new LinkedHashMap<>());

        assertTrue(registered.isEmpty());
    }

    @Test
    void shouldIgnoreResourcesEvenWhenClientExposesThem() {
        McpClient client = new FakeMcpClient(List.of(
                new McpToolDefinition("mcp.echo", "echo", textSchema("text"))
        )) {
            @Override
            public List<McpResourceDefinition> discoverResources() {
                return List.of(new McpResourceDefinition("mcp://resource/readme", "readme", "desc", "text/plain"));
            }
        };

        Map<String, AiRegisteredTool> registry = new LinkedHashMap<>();
        List<AiRegisteredTool> registered = new McpToolRegistryBootstrap(client, new McpToolSpecAdapter())
                .discoverAndRegister(registry);

        assertEquals(1, registered.size());
        assertTrue(registry.containsKey("mcp.echo"));
        assertFalse(registry.containsKey("mcp.resource.read"));
    }

    @Test
    void shouldRejectDuplicateToolNameInsideDiscoverResult() {
        McpClient client = new FakeMcpClient(List.of(
                new McpToolDefinition("mcp.echo", "first", textSchema("text")),
                new McpToolDefinition("mcp.echo", "second", textSchema("text"))
        ));
        McpToolRegistryBootstrap bootstrap = new McpToolRegistryBootstrap(client, new McpToolSpecAdapter());

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> bootstrap.discoverAndRegister(new LinkedHashMap<>())
        );

        assertTrue(ex.getMessage().contains("Duplicate tool name detected"));
    }

    @Test
    void shouldKeepTargetRegistryUnchangedWhenDiscoverHasDuplicateNames() {
        McpClient client = new FakeMcpClient(List.of(
                new McpToolDefinition("mcp.echo", "first", textSchema("text")),
                new McpToolDefinition("mcp.echo", "second", textSchema("text"))
        ));
        McpToolRegistryBootstrap bootstrap = new McpToolRegistryBootstrap(client, new McpToolSpecAdapter());
        Map<String, AiRegisteredTool> registry = new LinkedHashMap<>();
        registry.put("local.tool", registeredTool("local.tool", "local"));

        assertThrows(IllegalStateException.class, () -> bootstrap.discoverAndRegister(registry));

        assertEquals(1, registry.size());
        assertTrue(registry.containsKey("local.tool"));
        assertFalse(registry.containsKey("mcp.echo"));
    }

    @Test
    void shouldRejectToolNameWhenAlreadyExistsInTargetRegistry() {
        McpClient client = new FakeMcpClient(List.of(
                new McpToolDefinition("mcp.echo", "echo", textSchema("text"))
        ));
        McpToolRegistryBootstrap bootstrap = new McpToolRegistryBootstrap(client, new McpToolSpecAdapter());
        Map<String, AiRegisteredTool> registry = new LinkedHashMap<>();
        registry.put("mcp.echo", registeredTool("mcp.echo", "local"));

        assertThrows(IllegalStateException.class, () -> bootstrap.discoverAndRegister(registry));
    }

    @Test
    void shouldReportMultipleDuplicateToolNamesInStableOrder() {
        McpClient client = new FakeMcpClient(List.of(
                new McpToolDefinition("writeFile", "duplicate write", textSchema("text")),
                new McpToolDefinition("readFile", "duplicate read", textSchema("text"))
        ));
        McpToolRegistryBootstrap bootstrap = new McpToolRegistryBootstrap(client, new McpToolSpecAdapter());
        Map<String, AiRegisteredTool> registry = new LinkedHashMap<>();
        registry.put("readFile", registeredTool("readFile", "local-read"));
        registry.put("writeFile", registeredTool("writeFile", "local-write"));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> bootstrap.discoverAndRegister(registry)
        );

        assertEquals("Duplicate tool names detected: readFile, writeFile", ex.getMessage());
    }

    @Test
    void shouldKeepTargetRegistryUnchangedWhenToolNameAlreadyExistsInTargetRegistry() {
        McpClient client = new FakeMcpClient(List.of(
                new McpToolDefinition("mcp.echo", "echo", textSchema("text")),
                new McpToolDefinition("mcp.sum", "sum", textSchema("values"))
        ));
        McpToolRegistryBootstrap bootstrap = new McpToolRegistryBootstrap(client, new McpToolSpecAdapter());
        Map<String, AiRegisteredTool> registry = new LinkedHashMap<>();
        registry.put("mcp.echo", registeredTool("mcp.echo", "existing-echo"));
        registry.put("local.tool", registeredTool("local.tool", "local"));

        assertThrows(IllegalStateException.class, () -> bootstrap.discoverAndRegister(registry));

        assertEquals(2, registry.size());
        assertTrue(registry.containsKey("mcp.echo"));
        assertTrue(registry.containsKey("local.tool"));
        assertFalse(registry.containsKey("mcp.sum"));
    }

    @Test
    void shouldRejectNullTargetRegistry() {
        McpToolRegistryBootstrap bootstrap = new McpToolRegistryBootstrap(
                new FakeMcpClient(List.of(new McpToolDefinition("mcp.echo", "echo", textSchema("text")))),
                new McpToolSpecAdapter()
        );

        assertThrows(NullPointerException.class, () -> bootstrap.discoverAndRegister(null));
    }

    @Test
    void shouldTreatNullDiscoveredToolsAsEmpty() {
        McpToolRegistryBootstrap bootstrap = new McpToolRegistryBootstrap(
                new FakeMcpClient(null),
                new McpToolSpecAdapter()
        );

        List<AiRegisteredTool> registered = bootstrap.discoverAndRegister(new LinkedHashMap<>());

        assertEquals(List.of(), registered);
    }

    private static AiRegisteredTool registeredTool(String name, String description) {
        AiToolSpec spec = new AiToolSpec(name, description, textSchema("text"));
        return new AiRegisteredTool(
                spec.name(),
                spec.description(),
                new AiAgentParameterSchema(spec.inputSchema()),
                (request, memoryId) -> ""
        );
    }

    private static ObjectNode textSchema(String field) {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.putObject("properties").putObject(field).put("type", "string");
        schema.putArray("required").add(field);
        return schema;
    }

    private static class FakeMcpClient implements McpClient {
        private final List<McpToolDefinition> tools;

        private FakeMcpClient(List<McpToolDefinition> tools) {
            this.tools = tools;
        }

        @Override
        public List<McpToolDefinition> discoverTools() {
            return tools;
        }

        @Override
        public McpToolResult invoke(McpInvokeRequest request) {
            throw new UnsupportedOperationException("invoke is out of current scope");
        }
    }
}
