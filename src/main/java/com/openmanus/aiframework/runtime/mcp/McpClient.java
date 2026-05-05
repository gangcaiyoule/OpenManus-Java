package com.openmanus.aiframework.runtime.mcp;

import java.util.List;

public interface McpClient {

    List<McpToolDefinition> discoverTools();

    McpToolResult invoke(McpInvokeRequest request);

    default List<McpResourceDefinition> discoverResources() {
        return List.of();
    }

    default McpResourceReadResult readResource(String resourceUri) {
        throw new UnsupportedOperationException("MCP resource read is not supported");
    }
}
