package com.openmanus.infra.config;

import com.openmanus.agent.coordination.AgentCoordinator;
import com.openmanus.agent.tool.BrowserTool;
import com.openmanus.agent.tool.PythonExecutionTool;
import com.openmanus.agent.tool.SearchTool;
import com.openmanus.agent.tool.ShellTool;
import com.openmanus.agent.tool.TaskReflectionTool;
import com.openmanus.agent.tool.WebFetchTool;
import com.openmanus.agent.execution.AgentExecutionService;
import com.openmanus.aiframework.runtime.AiChatModel;
import com.openmanus.aiframework.runtime.AiCodeSandbox;
import com.openmanus.aiframework.runtime.AiMemoryProvider;
import com.openmanus.aiframework.runtime.AiProxyConfig;
import com.openmanus.aiframework.runtime.AiSearchConfig;
import com.openmanus.aiframework.runtime.AiSessionSandboxGateway;
import com.openmanus.aiframework.runtime.AiToolResultArtifactStore;
import com.openmanus.aiframework.tool.AiRegisteredTool;
import com.openmanus.aiframework.tool.AiToolRegistry;
import com.openmanus.aiframework.tool.mcp.McpToolRegistryBootstrap;
import com.openmanus.domain.service.ExecutionEventPort;
import com.openmanus.sandbox.support.SandboxPathResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Agent 架构配置：
 * 将当前默认工具集注册到统一执行协调器。
 */
@Configuration
public class AgentArchitectureConfig {

    @Bean
    public SandboxPathResolver sandboxPathResolver(AiSessionSandboxGateway sessionSandboxGateway) {
        return new SandboxPathResolver(sessionSandboxGateway);
    }

    @Bean
    public BrowserTool browserTool(AiSessionSandboxGateway sessionSandboxGateway,
                                   ExecutionEventPort executionEventPort) {
        return new BrowserTool(sessionSandboxGateway, executionEventPort);
    }

    @Bean
    public PythonExecutionTool pythonExecutionTool(AiCodeSandbox sandbox, SandboxPathResolver sandboxPathResolver) {
        return new PythonExecutionTool(sandbox, sandboxPathResolver);
    }

    @Bean
    public SearchTool searchTool(AiSearchConfig searchConfig,
                                 ExecutionEventPort executionEventPort,
                                 AiSessionSandboxGateway sessionSandboxGateway) {
        return new SearchTool(searchConfig, executionEventPort, sessionSandboxGateway);
    }

    @Bean
    public WebFetchTool webFetchTool(AiSessionSandboxGateway sessionSandboxGateway,
                                     AiProxyConfig proxyConfig,
                                     ExecutionEventPort executionEventPort) {
        return new WebFetchTool(sessionSandboxGateway, proxyConfig, executionEventPort);
    }

    @Bean
    public ShellTool shellTool(AiSessionSandboxGateway sessionSandboxGateway,
                               SandboxPathResolver sandboxPathResolver,
                               OpenManusProperties properties,
                               Optional<AiToolResultArtifactStore> toolResultArtifactStore) {
        return new ShellTool(
                sessionSandboxGateway,
                sandboxPathResolver,
                toolResultArtifactStore.orElse(null),
                properties.getChatMemory().isShellToolEnabled(),
                properties.getChatMemory().getShellToolTimeoutSeconds(),
                properties.getChatMemory().getShellToolMaxOutputChars()
        );
    }

    @Bean
    public TaskReflectionTool taskReflectionTool() {
        return new TaskReflectionTool();
    }

    @Bean
    public AgentExecutionService agentExecutionService(AgentCoordinator agentCoordinator,
                                                       @Qualifier(AsyncConfig.ASYNC_EXECUTOR_NAME) Executor asyncExecutor) {
        return new AgentExecutionService(agentCoordinator, asyncExecutor);
    }

    AgentCoordinator agentCoordinator(
            AiChatModel chatModel,
            AiMemoryProvider chatMemoryProvider,
            OpenManusProperties properties,
            BrowserTool browserTool,
            PythonExecutionTool pythonExecutionTool,
            SearchTool searchTool,
            WebFetchTool webFetchTool,
            ShellTool shellTool,
            TaskReflectionTool taskReflectionTool) {
        return agentCoordinator(chatModel, chatMemoryProvider, properties, null, null,
                browserTool, pythonExecutionTool, searchTool, webFetchTool, shellTool, taskReflectionTool);
    }

    @Bean
    public AgentCoordinator agentCoordinator(
            AiChatModel chatModel,
            AiMemoryProvider chatMemoryProvider,
            OpenManusProperties properties,
            AiToolResultArtifactStore toolResultArtifactStore,
            BrowserTool browserTool,
            PythonExecutionTool pythonExecutionTool,
            SearchTool searchTool,
            WebFetchTool webFetchTool,
            ShellTool shellTool,
            TaskReflectionTool taskReflectionTool,
            Optional<McpToolRegistryBootstrap> mcpToolRegistryBootstrap,
            ExecutionEventPort executionEventPort) {
        return agentCoordinator(
                chatModel,
                chatMemoryProvider,
                properties,
                toolResultArtifactStore,
                mcpToolRegistryBootstrap.orElse(null),
                executionEventPort,
                browserTool,
                pythonExecutionTool,
                searchTool,
                webFetchTool,
                shellTool,
                taskReflectionTool
        );
    }

    public AgentCoordinator agentCoordinator(
            AiChatModel chatModel,
            AiMemoryProvider chatMemoryProvider,
            OpenManusProperties properties,
            AiToolResultArtifactStore toolResultArtifactStore,
            ExecutionEventPort executionEventPort,
            BrowserTool browserTool,
            PythonExecutionTool pythonExecutionTool,
            SearchTool searchTool,
            WebFetchTool webFetchTool,
            ShellTool shellTool,
            TaskReflectionTool taskReflectionTool) {
        return agentCoordinator(
                chatModel,
                chatMemoryProvider,
                properties,
                toolResultArtifactStore,
                null,
                executionEventPort,
                browserTool,
                pythonExecutionTool,
                searchTool,
                webFetchTool,
                shellTool,
                taskReflectionTool
        );
    }

    AgentCoordinator agentCoordinator(
            AiChatModel chatModel,
            AiMemoryProvider chatMemoryProvider,
            OpenManusProperties properties,
            AiToolResultArtifactStore toolResultArtifactStore,
            McpToolRegistryBootstrap mcpToolRegistryBootstrap,
            ExecutionEventPort executionEventPort,
            BrowserTool browserTool,
            PythonExecutionTool pythonExecutionTool,
            SearchTool searchTool,
            WebFetchTool webFetchTool,
            ShellTool shellTool,
            TaskReflectionTool taskReflectionTool) {
        AgentCoordinator.Builder builder = AgentCoordinator.builder()
                .aiChatModel(chatModel)
                .aiMemoryProvider(chatMemoryProvider)
                .enableToolResultCompaction(properties.getChatMemory().isCompactToolResultsEnabled())
                .memoryToolResultMaxChars(properties.getChatMemory().getToolResultMaxChars())
                .compactToolResultHeadChars(properties.getChatMemory().getCompactToolResultHeadChars())
                .compactToolResultTailChars(properties.getChatMemory().getCompactToolResultTailChars())
                .modelContextMaxMessages(properties.getChatMemory().getModelContextMaxMessages())
                .modelContextMaxTotalMessages(properties.getChatMemory().getModelContextMaxTotalMessages())
                .modelContextMaxApproxTokens(properties.getChatMemory().getModelContextMaxApproxTokens())
                .modelContextTokenCountMode(properties.getChatMemory().getModelContextTokenCountMode())
                .modelContextTokenizerModel(resolveModelContextTokenizerModel(properties))
                .maxIterations(properties.getChatMemory().getReactMaxIterations())
                .maxExecutionSeconds(properties.getChatMemory().getReactMaxExecutionSeconds())
                .repeatedToolCallThreshold(properties.getChatMemory().getReactRepeatedToolCallThreshold())
                .taskStatePlanMaxChars(properties.getChatMemory().getTaskStatePlanMaxChars())
                .taskStateInProgressMaxChars(properties.getChatMemory().getTaskStateInProgressMaxChars())
                .taskStateLastFailureMaxChars(properties.getChatMemory().getTaskStateLastFailureMaxChars())
                .taskStateTodoMaxItems(properties.getChatMemory().getTaskStateTodoMaxItems())
                .taskStateTodoItemMaxChars(properties.getChatMemory().getTaskStateTodoItemMaxChars())
                .enableToolResultOffload(properties.getChatMemory().isToolResultOffloadEnabled())
                .toolResultOffloadMinChars(properties.getChatMemory().getToolResultOffloadMinChars())
                .toolResultOffloadHeadChars(properties.getChatMemory().getToolResultOffloadHeadChars())
                .toolResultOffloadTailChars(properties.getChatMemory().getToolResultOffloadTailChars())
                .enableToolResultRehydrate(properties.getChatMemory().isToolResultRehydrateEnabled())
                .toolResultRehydrateMaxChars(properties.getChatMemory().getToolResultRehydrateMaxChars())
                .toolResultRehydrateMaxPerRound(properties.getChatMemory().getToolResultRehydrateMaxPerRound())
                .toolResultArtifactStore(toolResultArtifactStore)
                .executionEventPort(executionEventPort)
                .browserTool(browserTool)
                .pythonExecutionTool(pythonExecutionTool)
                .toolFromObject(searchTool)
                .toolFromObject(webFetchTool)
                .shellTool(properties.getChatMemory().isShellToolEnabled() ? shellTool : null)
                .taskReflectionTool(taskReflectionTool);

        registerMcpTools(
                builder,
                properties,
                mcpToolRegistryBootstrap,
                browserTool,
                pythonExecutionTool,
                searchTool,
                webFetchTool,
                shellTool,
                taskReflectionTool
        );
        return builder.build();
    }

    private static String resolveModelContextTokenizerModel(OpenManusProperties properties) {
        if (properties == null || properties.getLlm() == null || properties.getLlm().getDefaultLlm() == null) {
            return "";
        }
        String model = properties.getLlm().getDefaultLlm().getModel();
        return model == null ? "" : model.trim();
    }

    private static void registerMcpTools(
            AgentCoordinator.Builder builder,
            OpenManusProperties properties,
            McpToolRegistryBootstrap bootstrap,
            BrowserTool browserTool,
            PythonExecutionTool pythonExecutionTool,
            SearchTool searchTool,
            WebFetchTool webFetchTool,
            ShellTool shellTool,
            TaskReflectionTool taskReflectionTool
    ) {
        if (bootstrap == null || properties == null || !properties.getMcp().isEnabled()) {
            return;
        }
        Map<String, AiRegisteredTool> localToolRegistry = new LinkedHashMap<>();
        appendLocalTools(localToolRegistry, browserTool);
        appendLocalTools(localToolRegistry, pythonExecutionTool);
        appendLocalTools(localToolRegistry, searchTool);
        appendLocalTools(localToolRegistry, webFetchTool);
        if (properties.getChatMemory().isShellToolEnabled()) {
            appendLocalTools(localToolRegistry, shellTool);
        }
        appendLocalTools(localToolRegistry, taskReflectionTool);

        List<AiRegisteredTool> mcpTools = bootstrap.discoverAndRegister(localToolRegistry);
        for (AiRegisteredTool mcpTool : mcpTools) {
            builder.tool(mcpTool);
        }
    }

    private static void appendLocalTools(Map<String, AiRegisteredTool> targetRegistry, Object toolObject) {
        if (toolObject == null) {
            return;
        }
        for (AiRegisteredTool localTool : AiToolRegistry.scan(toolObject)) {
            AiRegisteredTool existing = targetRegistry.putIfAbsent(localTool.name(), localTool);
            if (existing != null) {
                throw new IllegalStateException("Duplicate tool name detected: " + localTool.name());
            }
        }
    }
}
