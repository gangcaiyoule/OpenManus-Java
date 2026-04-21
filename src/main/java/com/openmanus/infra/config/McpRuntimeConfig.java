package com.openmanus.infra.config;

import com.openmanus.aiframework.runtime.mcp.McpClient;
import com.openmanus.aiframework.tool.mcp.McpToolRegistryBootstrap;
import com.openmanus.aiframework.tool.mcp.McpToolSpecAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP runtime wiring.
 */
@Configuration
public class McpRuntimeConfig {

    @Bean
    @ConditionalOnBean(McpClient.class)
    public McpToolRegistryBootstrap mcpToolRegistryBootstrap(
            McpClient mcpClient
    ) {
        return new McpToolRegistryBootstrap(mcpClient, new McpToolSpecAdapter());
    }
}
