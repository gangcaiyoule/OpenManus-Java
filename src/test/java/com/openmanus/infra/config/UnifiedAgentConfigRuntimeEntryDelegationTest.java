package com.openmanus.infra.config;

import com.openmanus.agent.impl.unified.UnifiedAgent;
import com.openmanus.agent.tool.BrowserTool;
import com.openmanus.agent.tool.FileTool;
import com.openmanus.agent.tool.PythonTool;
import com.openmanus.agent.tool.ReflectionTool;
import com.openmanus.aiframework.runtime.AiChatModel;
import com.openmanus.aiframework.runtime.AiMemoryProvider;
import com.openmanus.aiframework.runtime.AiToolResultArtifactStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class UnifiedAgentConfigRuntimeEntryDelegationTest {

    @Test
    void helperOverloadShouldDelegateToRuntimeFirstBeanMethod() {
        class CapturingUnifiedAgentConfig extends UnifiedAgentConfig {
            private AiChatModel capturedChatModel;
            private AiMemoryProvider capturedMemoryProvider;
            private OpenManusProperties capturedProperties;
            private AiToolResultArtifactStore capturedArtifactStore;
            private BrowserTool capturedBrowserTool;
            private PythonTool capturedPythonTool;
            private FileTool capturedFileTool;
            private ReflectionTool capturedReflectionTool;
            private final UnifiedAgent delegateResult = mock(UnifiedAgent.class);

            @Override
            public UnifiedAgent unifiedAgent(
                    AiChatModel chatModel,
                    AiMemoryProvider chatMemoryProvider,
                    OpenManusProperties properties,
                    AiToolResultArtifactStore toolResultArtifactStore,
                    BrowserTool browserTool,
                    PythonTool pythonTool,
                    FileTool fileTool,
                    ReflectionTool reflectionTool) {
                this.capturedChatModel = chatModel;
                this.capturedMemoryProvider = chatMemoryProvider;
                this.capturedProperties = properties;
                this.capturedArtifactStore = toolResultArtifactStore;
                this.capturedBrowserTool = browserTool;
                this.capturedPythonTool = pythonTool;
                this.capturedFileTool = fileTool;
                this.capturedReflectionTool = reflectionTool;
                return delegateResult;
            }
        }

        CapturingUnifiedAgentConfig config = new CapturingUnifiedAgentConfig();
        AiChatModel chatModel = mock(AiChatModel.class);
        AiMemoryProvider memoryProvider = mock(AiMemoryProvider.class);
        OpenManusProperties properties = new OpenManusProperties();
        BrowserTool browserTool = mock(BrowserTool.class);
        PythonTool pythonTool = mock(PythonTool.class);
        FileTool fileTool = mock(FileTool.class);
        ReflectionTool reflectionTool = mock(ReflectionTool.class);

        UnifiedAgent result = config.unifiedAgent(
                chatModel,
                memoryProvider,
                properties,
                browserTool,
                pythonTool,
                fileTool,
                reflectionTool
        );

        assertSame(config.delegateResult, result);
        assertSame(chatModel, config.capturedChatModel);
        assertSame(memoryProvider, config.capturedMemoryProvider);
        assertSame(properties, config.capturedProperties);
        assertSame(browserTool, config.capturedBrowserTool);
        assertSame(pythonTool, config.capturedPythonTool);
        assertSame(fileTool, config.capturedFileTool);
        assertSame(reflectionTool, config.capturedReflectionTool);
        assertSame(null, config.capturedArtifactStore);
    }
}
