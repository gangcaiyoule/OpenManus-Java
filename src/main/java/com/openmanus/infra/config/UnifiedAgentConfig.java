package com.openmanus.infra.config;

import com.openmanus.agent.impl.unified.UnifiedAgent;
import com.openmanus.agent.tool.BrowserTool;
import com.openmanus.agent.tool.FileTool;
import com.openmanus.agent.tool.PythonTool;
import com.openmanus.agent.tool.ReflectionTool;
import com.openmanus.agent.tool.support.SandboxPathResolver;
import com.openmanus.agent.workflow.UnifiedWorkflow;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * 单智能体配置：
 * 将全部工具直接注册到一个执行器。
 */
@Configuration
public class UnifiedAgentConfig {

    @Bean
    public SandboxPathResolver sandboxPathResolver(AiSessionSandboxGateway sessionSandboxGateway) {
        return new SandboxPathResolver(sessionSandboxGateway);
    }

    @Bean
    public BrowserTool browserTool(AiSessionSandboxGateway sessionSandboxGateway,
                                   AiSearchConfig searchConfig,
                                   AiProxyConfig proxyConfig) {
        return new BrowserTool(sessionSandboxGateway, searchConfig, proxyConfig);
    }

    @Bean
    public PythonTool pythonTool(AiCodeSandbox sandbox, SandboxPathResolver sandboxPathResolver) {
        return new PythonTool(sandbox, sandboxPathResolver);
    }

    @Bean
    public FileTool fileTool(SandboxPathResolver sandboxPathResolver) {
        return new FileTool(sandboxPathResolver);
    }

    @Bean
    public ReflectionTool reflectionTool() {
        return new ReflectionTool();
    }

    @Bean
    public UnifiedWorkflow unifiedWorkflow(UnifiedAgent unifiedAgent,
                                           @Qualifier(AsyncConfig.ASYNC_EXECUTOR_NAME) Executor asyncExecutor) {
        return new UnifiedWorkflow(unifiedAgent, asyncExecutor);
    }

    UnifiedAgent unifiedAgent(
            AiChatModel chatModel,
            AiMemoryProvider chatMemoryProvider,
            OpenManusProperties properties,
            BrowserTool browserTool,
            PythonTool pythonTool,
            FileTool fileTool,
            ReflectionTool reflectionTool) {
        return unifiedAgent(chatModel, chatMemoryProvider, properties, null,
                browserTool, pythonTool, fileTool, reflectionTool);
    }

    @Bean
    public UnifiedAgent unifiedAgent(
            AiChatModel chatModel,
            AiMemoryProvider chatMemoryProvider,
            OpenManusProperties properties,
            AiToolResultArtifactStore toolResultArtifactStore,
            BrowserTool browserTool,
            PythonTool pythonTool,
            FileTool fileTool,
            ReflectionTool reflectionTool,
            Optional<McpToolRegistryBootstrap> mcpToolRegistryBootstrap) {
        return unifiedAgent(
                chatModel,
                chatMemoryProvider,
                properties,
                toolResultArtifactStore,
                mcpToolRegistryBootstrap.orElse(null),
                browserTool,
                pythonTool,
                fileTool,
                reflectionTool
        );
    }

    public UnifiedAgent unifiedAgent(
            AiChatModel chatModel,
            AiMemoryProvider chatMemoryProvider,
            OpenManusProperties properties,
            AiToolResultArtifactStore toolResultArtifactStore,
            BrowserTool browserTool,
            PythonTool pythonTool,
            FileTool fileTool,
            ReflectionTool reflectionTool) {
        return unifiedAgent(
                chatModel,
                chatMemoryProvider,
                properties,
                toolResultArtifactStore,
                null,
                browserTool,
                pythonTool,
                fileTool,
                reflectionTool
        );
    }

    UnifiedAgent unifiedAgent(
            AiChatModel chatModel,
            AiMemoryProvider chatMemoryProvider,
            OpenManusProperties properties,
            AiToolResultArtifactStore toolResultArtifactStore,
            McpToolRegistryBootstrap mcpToolRegistryBootstrap,
            BrowserTool browserTool,
            PythonTool pythonTool,
            FileTool fileTool,
            ReflectionTool reflectionTool) {
        UnifiedAgent.Builder builder = UnifiedAgent.builder()
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
                .browserTool(browserTool)
                .pythonTool(pythonTool)
                .fileTool(fileTool)
                .reflectionTool(reflectionTool);

        registerMcpTools(
                builder,
                properties,
                mcpToolRegistryBootstrap,
                browserTool,
                pythonTool,
                fileTool,
                reflectionTool
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
            UnifiedAgent.Builder builder,
            OpenManusProperties properties,
            McpToolRegistryBootstrap bootstrap,
            BrowserTool browserTool,
            PythonTool pythonTool,
            FileTool fileTool,
            ReflectionTool reflectionTool
    ) {
        if (bootstrap == null || properties == null || !properties.getMcp().isEnabled()) {
            return;
        }
        Map<String, AiRegisteredTool> localToolRegistry = new LinkedHashMap<>();
        appendLocalTools(localToolRegistry, browserTool);
        appendLocalTools(localToolRegistry, pythonTool);
        appendLocalTools(localToolRegistry, fileTool);
        appendLocalTools(localToolRegistry, reflectionTool);

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
