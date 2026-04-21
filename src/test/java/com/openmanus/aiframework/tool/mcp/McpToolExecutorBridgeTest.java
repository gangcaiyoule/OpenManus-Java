package com.openmanus.aiframework.tool.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.openmanus.aiframework.runtime.mcp.McpClient;
import com.openmanus.aiframework.runtime.mcp.McpInvokeRequest;
import com.openmanus.aiframework.runtime.mcp.McpToolDefinition;
import com.openmanus.aiframework.runtime.mcp.McpToolResult;
import com.openmanus.aiframework.tool.AiToolExecutionRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolExecutorBridgeTest {

    @Test
    void shouldInvokeMcpClientWithNormalizedArguments() {
        AtomicReference<McpInvokeRequest> captured = new AtomicReference<>();
        McpClient client = new CapturingClient(captured, new McpToolResult("mcp.echo", "hello-bridge", false));
        McpToolExecutorBridge bridge = new McpToolExecutorBridge(client);

        String result = bridge.execute(
                new AiToolExecutionRequest("call_1", "mcp.echo", "{\"text\":\"hello\"}"),
                "memory-1"
        );

        assertEquals("hello-bridge", result);
        assertNotNull(captured.get());
        assertEquals("mcp.echo", captured.get().toolName());
        assertEquals("hello", captured.get().arguments().path("text").asText());
    }

    @Test
    void shouldUseEmptyObjectWhenToolArgumentsAreBlank() {
        AtomicReference<McpInvokeRequest> captured = new AtomicReference<>();
        McpClient client = new CapturingClient(captured, new McpToolResult("mcp.echo", "ok", false));
        McpToolExecutorBridge bridge = new McpToolExecutorBridge(client);

        String result = bridge.execute(new AiToolExecutionRequest("call_2", "mcp.echo", " "), null);

        assertEquals("ok", result);
        JsonNode args = captured.get().arguments();
        assertNotNull(args);
        assertTrue(args.isObject());
        assertEquals(0, args.size());
    }

    @Test
    void shouldRejectInvalidJsonArguments() {
        McpToolExecutorBridge bridge = new McpToolExecutorBridge(new NoopClient());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> bridge.execute(new AiToolExecutionRequest("call_3", "mcp.echo", "{invalid"), null)
        );

        assertEquals("Invalid MCP tool arguments JSON", ex.getMessage());
    }

    @Test
    void shouldPropagateClientException() {
        McpClient client = new NoopClient() {
            @Override
            public McpToolResult invoke(McpInvokeRequest request) {
                throw new IllegalStateException("mcp client unavailable");
            }
        };
        McpToolExecutorBridge bridge = new McpToolExecutorBridge(client);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> bridge.execute(new AiToolExecutionRequest("call_4", "mcp.echo", "{}"), null)
        );

        assertEquals("mcp client unavailable", ex.getMessage());
    }

    @Test
    void shouldThrowWhenMcpResultIsMarkedAsError() {
        McpClient client = new NoopClient() {
            @Override
            public McpToolResult invoke(McpInvokeRequest request) {
                return new McpToolResult("mcp.echo", "downstream timeout", true);
            }
        };
        McpToolExecutorBridge bridge = new McpToolExecutorBridge(client);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> bridge.execute(new AiToolExecutionRequest("call_5", "mcp.echo", "{\"text\":\"hello\"}"), null)
        );

        assertEquals("mcp tool invocation failed: tool=mcp.echo, error=downstream timeout", ex.getMessage());
    }

    @Test
    void shouldUseRequestToolNameWhenErrorResultToolNameIsBlank() {
        McpClient client = new NoopClient() {
            @Override
            public McpToolResult invoke(McpInvokeRequest request) {
                return new McpToolResult(" ", "resource offline", true);
            }
        };
        McpToolExecutorBridge bridge = new McpToolExecutorBridge(client);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> bridge.execute(new AiToolExecutionRequest("call_6", "mcp.echo", "{}"), null)
        );

        assertEquals("mcp tool invocation failed: tool=mcp.echo, error=resource offline", ex.getMessage());
    }

    private record CapturingClient(
            AtomicReference<McpInvokeRequest> captured,
            McpToolResult fixedResult
    ) implements McpClient {

        @Override
        public List<McpToolDefinition> discoverTools() {
            return List.of();
        }

        @Override
        public McpToolResult invoke(McpInvokeRequest request) {
            captured.set(request);
            return fixedResult;
        }
    }

    private static class NoopClient implements McpClient {

        @Override
        public List<McpToolDefinition> discoverTools() {
            return List.of();
        }

        @Override
        public McpToolResult invoke(McpInvokeRequest request) {
            return new McpToolResult(request.toolName(), JsonNodeFactory.instance.objectNode().toString(), false);
        }
    }
}
