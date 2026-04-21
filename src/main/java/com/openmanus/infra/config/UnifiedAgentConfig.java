package com.openmanus.infra.config;

import com.openmanus.agent.impl.unified.UnifiedAgent;
import com.openmanus.agent.tool.BrowserTool;
import com.openmanus.agent.tool.FileTool;
import com.openmanus.agent.tool.PythonTool;
import com.openmanus.agent.tool.ReflectionTool;
import com.openmanus.infra.memory.ToolResultArtifactStore;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 单智能体配置：
 * 将全部工具直接注册到一个执行器。
 */
@Configuration
public class UnifiedAgentConfig {

    UnifiedAgent unifiedAgent(
            ChatModel chatModel,
            ChatMemoryProvider chatMemoryProvider,
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
            ChatModel chatModel,
            ChatMemoryProvider chatMemoryProvider,
            OpenManusProperties properties,
            ToolResultArtifactStore toolResultArtifactStore,
            BrowserTool browserTool,
            PythonTool pythonTool,
            FileTool fileTool,
            ReflectionTool reflectionTool) {
        return UnifiedAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .enableToolResultCompaction(properties.getChatMemory().isCompactToolResultsEnabled())
                .memoryToolResultMaxChars(properties.getChatMemory().getToolResultMaxChars())
                .compactToolResultHeadChars(properties.getChatMemory().getCompactToolResultHeadChars())
                .compactToolResultTailChars(properties.getChatMemory().getCompactToolResultTailChars())
                .modelContextMaxMessages(properties.getChatMemory().getModelContextMaxMessages())
                .modelContextMaxTotalMessages(properties.getChatMemory().getModelContextMaxTotalMessages())
                .modelContextMaxApproxTokens(properties.getChatMemory().getModelContextMaxApproxTokens())
                .maxIterations(properties.getChatMemory().getReactMaxIterations())
                .maxExecutionSeconds(properties.getChatMemory().getReactMaxExecutionSeconds())
                .repeatedToolCallThreshold(properties.getChatMemory().getReactRepeatedToolCallThreshold())
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
                .reflectionTool(reflectionTool)
                .build();
    }
}
