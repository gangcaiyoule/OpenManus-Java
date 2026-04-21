package com.openmanus.aiframework.tool.mcp;

import com.openmanus.aiframework.runtime.mcp.McpClient;
import com.openmanus.aiframework.runtime.mcp.McpToolDefinition;
import com.openmanus.aiframework.runtime.model.AiAgentParameterSchema;
import com.openmanus.aiframework.runtime.model.AiToolSpec;
import com.openmanus.aiframework.tool.AiRegisteredTool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Discovers MCP tools and registers them into the same runtime registry shape used by local tools.
 */
public final class McpToolRegistryBootstrap {

    private final McpClient mcpClient;
    private final McpToolSpecAdapter specAdapter;
    private final McpToolExecutorBridge executorBridge;

    public McpToolRegistryBootstrap(McpClient mcpClient, McpToolSpecAdapter specAdapter) {
        this(
                Objects.requireNonNull(mcpClient, "mcpClient cannot be null"),
                Objects.requireNonNull(specAdapter, "specAdapter cannot be null"),
                new McpToolExecutorBridge(mcpClient)
        );
    }

    McpToolRegistryBootstrap(
            McpClient mcpClient,
            McpToolSpecAdapter specAdapter,
            McpToolExecutorBridge executorBridge
    ) {
        this.mcpClient = Objects.requireNonNull(mcpClient, "mcpClient cannot be null");
        this.specAdapter = Objects.requireNonNull(specAdapter, "specAdapter cannot be null");
        this.executorBridge = Objects.requireNonNull(executorBridge, "executorBridge cannot be null");
    }

    public List<AiRegisteredTool> discoverAndRegister(Map<String, AiRegisteredTool> targetRegistry) {
        Objects.requireNonNull(targetRegistry, "targetRegistry cannot be null");
        List<McpToolDefinition> discovered = mcpClient.discoverTools();
        List<McpToolDefinition> availableTools = discovered == null
                ? List.of()
                : discovered.stream().filter(Objects::nonNull).toList();
        if (availableTools.isEmpty()) {
            return List.of();
        }

        Map<String, AiRegisteredTool> staged = new LinkedHashMap<>();
        List<AiRegisteredTool> registered = new ArrayList<>();
        Set<String> duplicateToolNames = new LinkedHashSet<>();
        for (McpToolDefinition definition : availableTools) {
            AiToolSpec spec = specAdapter.toAiToolSpec(definition);
            String toolName = spec.name();
            if (targetRegistry.containsKey(toolName) || staged.containsKey(toolName)) {
                duplicateToolNames.add(toolName);
                continue;
            }
            AiRegisteredTool tool = toRegisteredTool(spec, executorBridge);
            staged.put(toolName, tool);
            registered.add(tool);
        }
        if (!duplicateToolNames.isEmpty()) {
            throw new IllegalStateException(buildDuplicateToolNameMessage(duplicateToolNames));
        }
        targetRegistry.putAll(staged);
        return List.copyOf(registered);
    }

    public List<AiRegisteredTool> discoverAndRegister() {
        Map<String, AiRegisteredTool> registry = new LinkedHashMap<>();
        return discoverAndRegister(registry);
    }

    private static AiRegisteredTool toRegisteredTool(AiToolSpec spec, McpToolExecutorBridge executorBridge) {
        return new AiRegisteredTool(
                spec.name(),
                spec.description(),
                new AiAgentParameterSchema(spec.inputSchema()),
                executorBridge::execute
        );
    }

    private static String buildDuplicateToolNameMessage(Set<String> duplicateToolNames) {
        List<String> stableNames = duplicateToolNames.stream()
                .sorted()
                .toList();
        if (stableNames.size() == 1) {
            return "Duplicate tool name detected: " + stableNames.get(0);
        }
        return "Duplicate tool names detected: " + String.join(", ", stableNames);
    }
}
