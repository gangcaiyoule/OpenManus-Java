package com.openmanus.aiframework.tool.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.openmanus.aiframework.runtime.mcp.McpClient;
import com.openmanus.aiframework.runtime.mcp.McpInvokeRequest;
import com.openmanus.aiframework.runtime.mcp.McpToolResult;
import com.openmanus.aiframework.tool.AiToolExecutionRequest;

import java.io.IOException;
import java.util.Objects;

/**
 * Bridges runtime tool execution calls to MCP client invoke requests.
 */
public final class McpToolExecutorBridge {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final McpClient mcpClient;

    public McpToolExecutorBridge(McpClient mcpClient) {
        this.mcpClient = Objects.requireNonNull(mcpClient, "mcpClient cannot be null");
    }

    public String execute(AiToolExecutionRequest request, Object memoryId) {
        Objects.requireNonNull(request, "request cannot be null");
        McpInvokeRequest invokeRequest = new McpInvokeRequest(
                request.name(),
                parseArguments(request.arguments())
        );
        McpToolResult result = mcpClient.invoke(invokeRequest);
        return normalizeResult(result, request.name());
    }

    private static JsonNode parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return JsonNodeFactory.instance.objectNode();
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(arguments);
            return node == null ? JsonNodeFactory.instance.objectNode() : node;
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid MCP tool arguments JSON", e);
        }
    }

    private static String normalizeResult(McpToolResult result, String fallbackToolName) {
        if (result == null) {
            throw new IllegalStateException("mcp client returned null result: tool=" + fallbackToolName);
        }
        if (result.error()) {
            String resultToolName = result.toolName();
            String toolName = (resultToolName == null || resultToolName.isBlank())
                    ? fallbackToolName
                    : resultToolName;
            throw new IllegalStateException("mcp tool invocation failed: tool=" + toolName + ", error=" + result.content());
        }
        return result.content();
    }
}
