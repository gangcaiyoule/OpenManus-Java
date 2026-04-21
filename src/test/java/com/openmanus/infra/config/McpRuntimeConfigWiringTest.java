package com.openmanus.infra.config;

import com.openmanus.aiframework.runtime.mcp.McpClient;
import com.openmanus.aiframework.runtime.mcp.McpInvokeRequest;
import com.openmanus.aiframework.runtime.mcp.McpResourceDefinition;
import com.openmanus.aiframework.runtime.mcp.McpToolDefinition;
import com.openmanus.aiframework.runtime.mcp.McpToolResult;
import com.openmanus.aiframework.tool.AiRegisteredTool;
import com.openmanus.aiframework.tool.mcp.McpToolRegistryBootstrap;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpRuntimeConfigWiringTest {

    @Test
    void shouldWireOnlyMcpToolDiscoveryIntoDefaultRuntimeConfig() {
        McpRuntimeConfig config = new McpRuntimeConfig();

        McpToolRegistryBootstrap bootstrap = config.mcpToolRegistryBootstrap(new ToolAndResourceMcpClient());
        Map<String, AiRegisteredTool> registry = new LinkedHashMap<>();
        bootstrap.discoverAndRegister(registry);

        assertTrue(registry.containsKey("mcp.echo"));
        assertFalse(registry.containsKey("mcp.resource.read"));
    }

    private static final class ToolAndResourceMcpClient implements McpClient {

        @Override
        public List<McpToolDefinition> discoverTools() {
            return List.of(new McpToolDefinition("mcp.echo", "echo", null));
        }

        @Override
        public McpToolResult invoke(McpInvokeRequest request) {
            return new McpToolResult(request.toolName(), "ok", false);
        }

        @Override
        public List<McpResourceDefinition> discoverResources() {
            return List.of(new McpResourceDefinition("mcp://resource/readme", "readme", "desc", "text/plain"));
        }
    }
}
