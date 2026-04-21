package com.openmanus.infra.config;

import com.openmanus.agent.base.AbstractAgentExecutor;
import com.openmanus.agent.context.ContextBudgetPolicy;
import com.openmanus.agent.context.ModelTokenizerModelContextTokenCounter;
import com.openmanus.agent.impl.unified.UnifiedAgent;
import com.openmanus.agent.tool.BrowserTool;
import com.openmanus.agent.tool.FileTool;
import com.openmanus.agent.tool.PythonTool;
import com.openmanus.agent.tool.ReflectionTool;
import com.openmanus.aiframework.runtime.AiChatModel;
import com.openmanus.aiframework.runtime.AiCodeSandbox;
import com.openmanus.aiframework.runtime.AiMemoryProvider;
import com.openmanus.aiframework.runtime.AiProxyConfig;
import com.openmanus.aiframework.runtime.AiSessionSandboxGateway;
import com.openmanus.aiframework.runtime.AiSearchConfig;
import com.openmanus.aiframework.runtime.mcp.McpClient;
import com.openmanus.aiframework.runtime.mcp.McpInvokeRequest;
import com.openmanus.aiframework.runtime.mcp.McpResourceDefinition;
import com.openmanus.aiframework.runtime.mcp.McpToolDefinition;
import com.openmanus.aiframework.runtime.mcp.McpToolResult;
import com.openmanus.aiframework.runtime.AiToolResultArtifactStore;
import com.openmanus.aiframework.tool.AiRegisteredTool;
import com.openmanus.aiframework.tool.mcp.McpToolRegistryBootstrap;
import com.openmanus.aiframework.tool.mcp.McpToolSpecAdapter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class Step2UnifiedAgentConfigRuntimeBehaviorTest {

    @Test
    void unifiedAgentBeanMethodShouldBindRuntimeDependenciesIntoExecutorState() throws Exception {
        UnifiedAgentConfig config = new UnifiedAgentConfig();
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setToolResultOffloadEnabled(false);
        properties.getChatMemory().setToolResultRehydrateEnabled(false);

        AiChatModel aiChatModel = mock(AiChatModel.class);
        AiMemoryProvider aiMemoryProvider = mock(AiMemoryProvider.class);
        AiToolResultArtifactStore artifactStore = mock(AiToolResultArtifactStore.class);

        UnifiedAgent agent = config.unifiedAgent(
                aiChatModel,
                aiMemoryProvider,
                properties,
                artifactStore,
                null,
                null,
                null,
                null
        );

        assertSame(aiChatModel, readExecutorField(agent, "aiChatModel"));
        assertSame(aiMemoryProvider, readExecutorField(agent, "aiMemoryProvider"));
        assertSame(artifactStore, readExecutorField(agent, "toolResultArtifactStore"));
    }

    @Test
    void unifiedAgentBeanMethodShouldMapChatMemoryProperties() throws Exception {
        UnifiedAgentConfig config = new UnifiedAgentConfig();
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setCompactToolResultsEnabled(true);
        properties.getChatMemory().setToolResultMaxChars(3500);
        properties.getChatMemory().setCompactToolResultHeadChars(280);
        properties.getChatMemory().setCompactToolResultTailChars(180);
        properties.getChatMemory().setModelContextMaxMessages(12);
        properties.getChatMemory().setModelContextMaxTotalMessages(20);
        properties.getChatMemory().setModelContextMaxApproxTokens(32000);
        properties.getChatMemory().setModelContextTokenCountMode("tokenizer");
        properties.getLlm().getDefaultLlm().setModel("gpt-4o-mini");
        properties.getChatMemory().setReactMaxIterations(6);
        properties.getChatMemory().setReactMaxExecutionSeconds(9);
        properties.getChatMemory().setReactRepeatedToolCallThreshold(3);
        properties.getChatMemory().setTaskStatePlanMaxChars(260);
        properties.getChatMemory().setTaskStateInProgressMaxChars(110);
        properties.getChatMemory().setTaskStateLastFailureMaxChars(280);
        properties.getChatMemory().setTaskStateTodoMaxItems(5);
        properties.getChatMemory().setTaskStateTodoItemMaxChars(90);
        properties.getChatMemory().setToolResultOffloadEnabled(true);
        properties.getChatMemory().setToolResultOffloadMinChars(1000);
        properties.getChatMemory().setToolResultOffloadHeadChars(100);
        properties.getChatMemory().setToolResultOffloadTailChars(90);
        properties.getChatMemory().setToolResultRehydrateEnabled(true);
        properties.getChatMemory().setToolResultRehydrateMaxChars(4096);
        properties.getChatMemory().setToolResultRehydrateMaxPerRound(4);

        AiToolResultArtifactStore artifactStore = mock(AiToolResultArtifactStore.class);
        UnifiedAgent agent = config.unifiedAgent(
                mock(AiChatModel.class),
                mock(AiMemoryProvider.class),
                properties,
                artifactStore,
                null,
                null,
                null,
                null
        );

        assertEquals(true, readExecutorField(agent, "enableToolResultCompaction"));
        assertEquals(3500, readExecutorField(agent, "memoryToolResultMaxChars"));
        assertEquals(280, readExecutorField(agent, "compactToolResultHeadChars"));
        assertEquals(180, readExecutorField(agent, "compactToolResultTailChars"));
        assertEquals(12, readExecutorField(agent, "modelContextMaxMessages"));
        assertEquals(20, readExecutorField(agent, "modelContextMaxTotalMessages"));
        assertEquals(32000, readExecutorField(agent, "modelContextMaxApproxTokens"));
        assertEquals("tokenizer", readExecutorField(agent, "modelContextTokenCountMode"));
        assertEquals("gpt-4o-mini", readExecutorField(agent, "modelContextTokenizerModel"));
        assertEquals(6, readExecutorField(agent, "maxIterations"));
        assertEquals(9, readExecutorField(agent, "maxExecutionSeconds"));
        assertEquals(3, readExecutorField(agent, "repeatedToolCallThreshold"));
        Object taskBudget = readExecutorField(agent, "taskStateBudgetPolicy");
        assertEquals(260, readField(taskBudget, "planMaxChars"));
        assertEquals(110, readField(taskBudget, "inProgressMaxChars"));
        assertEquals(280, readField(taskBudget, "lastFailureMaxChars"));
        assertEquals(5, readField(taskBudget, "todoMaxItems"));
        assertEquals(90, readField(taskBudget, "todoItemMaxChars"));
        assertEquals(true, readExecutorField(agent, "enableToolResultOffload"));
        assertEquals(1000, readExecutorField(agent, "toolResultOffloadMinChars"));
        assertEquals(100, readExecutorField(agent, "toolResultOffloadHeadChars"));
        assertEquals(90, readExecutorField(agent, "toolResultOffloadTailChars"));
        assertEquals(true, readExecutorField(agent, "enableToolResultRehydrate"));
        assertEquals(4096, readExecutorField(agent, "toolResultRehydrateMaxChars"));
        assertEquals(4, readExecutorField(agent, "toolResultRehydrateMaxPerRound"));
        assertSame(artifactStore, readExecutorField(agent, "toolResultArtifactStore"));

        ContextBudgetPolicy policy = (ContextBudgetPolicy) readExecutorField(agent, "contextBudgetPolicy");
        Field counterField = ContextBudgetPolicy.class.getDeclaredField("modelContextTokenCounter");
        counterField.setAccessible(true);
        Object counter = counterField.get(policy);
        assertEquals(true, counter instanceof ModelTokenizerModelContextTokenCounter);
    }

    @Test
    void unifiedAgentBeanMethodShouldSanitizeInvalidChatMemoryProperties() throws Exception {
        UnifiedAgentConfig config = new UnifiedAgentConfig();
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setCompactToolResultsEnabled(true);
        properties.getChatMemory().setToolResultMaxChars(-1);
        properties.getChatMemory().setCompactToolResultHeadChars(1);
        properties.getChatMemory().setCompactToolResultTailChars(1);
        properties.getChatMemory().setModelContextMaxMessages(-2);
        properties.getChatMemory().setModelContextMaxTotalMessages(-3);
        properties.getChatMemory().setModelContextMaxApproxTokens(-4);
        properties.getChatMemory().setModelContextTokenCountMode("invalid_mode");
        properties.getChatMemory().setReactMaxIterations(-5);
        properties.getChatMemory().setReactMaxExecutionSeconds(-6);
        properties.getChatMemory().setReactRepeatedToolCallThreshold(-7);
        properties.getChatMemory().setTaskStatePlanMaxChars(-11);
        properties.getChatMemory().setTaskStateInProgressMaxChars(-12);
        properties.getChatMemory().setTaskStateLastFailureMaxChars(-13);
        properties.getChatMemory().setTaskStateTodoMaxItems(-14);
        properties.getChatMemory().setTaskStateTodoItemMaxChars(-15);
        properties.getChatMemory().setToolResultOffloadEnabled(true);
        properties.getChatMemory().setToolResultOffloadMinChars(-8);
        properties.getChatMemory().setToolResultOffloadHeadChars(1);
        properties.getChatMemory().setToolResultOffloadTailChars(1);
        properties.getChatMemory().setToolResultRehydrateEnabled(true);
        properties.getChatMemory().setToolResultRehydrateMaxChars(-9);
        properties.getChatMemory().setToolResultRehydrateMaxPerRound(-10);

        AiToolResultArtifactStore artifactStore = mock(AiToolResultArtifactStore.class);
        UnifiedAgent agent = config.unifiedAgent(
                mock(AiChatModel.class),
                mock(AiMemoryProvider.class),
                properties,
                artifactStore,
                null,
                null,
                null,
                null
        );

        assertEquals(true, readExecutorField(agent, "enableToolResultCompaction"));
        assertEquals(256, readExecutorField(agent, "memoryToolResultMaxChars"));
        assertEquals(64, readExecutorField(agent, "compactToolResultHeadChars"));
        assertEquals(32, readExecutorField(agent, "compactToolResultTailChars"));
        assertEquals(0, readExecutorField(agent, "modelContextMaxMessages"));
        assertEquals(0, readExecutorField(agent, "modelContextMaxTotalMessages"));
        assertEquals(0, readExecutorField(agent, "modelContextMaxApproxTokens"));
        assertEquals("approx", readExecutorField(agent, "modelContextTokenCountMode"));
        assertEquals(0, readExecutorField(agent, "maxIterations"));
        assertEquals(0, readExecutorField(agent, "maxExecutionSeconds"));
        assertEquals(0, readExecutorField(agent, "repeatedToolCallThreshold"));
        Object taskBudget = readExecutorField(agent, "taskStateBudgetPolicy");
        assertEquals(240, readField(taskBudget, "planMaxChars"));
        assertEquals(120, readField(taskBudget, "inProgressMaxChars"));
        assertEquals(240, readField(taskBudget, "lastFailureMaxChars"));
        assertEquals(6, readField(taskBudget, "todoMaxItems"));
        assertEquals(120, readField(taskBudget, "todoItemMaxChars"));
        assertEquals(true, readExecutorField(agent, "enableToolResultOffload"));
        assertEquals(12000, readExecutorField(agent, "toolResultOffloadMinChars"));
        assertEquals(64, readExecutorField(agent, "toolResultOffloadHeadChars"));
        assertEquals(32, readExecutorField(agent, "toolResultOffloadTailChars"));
        assertEquals(true, readExecutorField(agent, "enableToolResultRehydrate"));
        assertEquals(8000, readExecutorField(agent, "toolResultRehydrateMaxChars"));
        assertEquals(0, readExecutorField(agent, "toolResultRehydrateMaxPerRound"));
        assertSame(artifactStore, readExecutorField(agent, "toolResultArtifactStore"));
    }

    @Test
    void helperOverloadShouldFailFastWithoutArtifactStoreWhenOffloadEnabledByDefault() {
        UnifiedAgentConfig config = new UnifiedAgentConfig();
        OpenManusProperties properties = new OpenManusProperties();

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> config.unifiedAgent(
                        mock(AiChatModel.class),
                        mock(AiMemoryProvider.class),
                        properties,
                        null,
                        null,
                        null,
                        null
                )
        );
        assertEquals(
                "toolResultArtifactStore must be configured when toolResultOffload is enabled",
                ex.getMessage()
        );
    }

    @Test
    void helperOverloadShouldFailFastWithoutArtifactStoreWhenOffloadExplicitlyEnabled() {
        UnifiedAgentConfig config = new UnifiedAgentConfig();
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setToolResultOffloadEnabled(true);
        properties.getChatMemory().setToolResultRehydrateEnabled(false);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> config.unifiedAgent(
                        mock(AiChatModel.class),
                        mock(AiMemoryProvider.class),
                        properties,
                        null,
                        null,
                        null,
                        null
                )
        );
        assertEquals(
                "toolResultArtifactStore must be configured when toolResultOffload is enabled",
                ex.getMessage()
        );
    }

    @Test
    void helperOverloadShouldAllowNullArtifactStoreWhenOffloadAndRehydrateDisabled() throws Exception {
        UnifiedAgentConfig config = new UnifiedAgentConfig();
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setToolResultOffloadEnabled(false);
        properties.getChatMemory().setToolResultRehydrateEnabled(false);

        UnifiedAgent agent = config.unifiedAgent(
                mock(AiChatModel.class),
                mock(AiMemoryProvider.class),
                properties,
                null,
                null,
                null,
                null
        );

        assertEquals(false, readExecutorField(agent, "enableToolResultOffload"));
        assertEquals(false, readExecutorField(agent, "enableToolResultRehydrate"));
        assertSame(null, readExecutorField(agent, "toolResultArtifactStore"));
    }

    @Test
    void helperOverloadShouldFailFastWithoutArtifactStoreWhenOnlyRehydrateEnabled() {
        UnifiedAgentConfig config = new UnifiedAgentConfig();
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setToolResultOffloadEnabled(false);
        properties.getChatMemory().setToolResultRehydrateEnabled(true);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> config.unifiedAgent(
                        mock(AiChatModel.class),
                        mock(AiMemoryProvider.class),
                        properties,
                        null,
                        null,
                        null,
                        null
                )
        );
        assertEquals(
                "toolResultArtifactStore must be configured when toolResultRehydrate is enabled",
                ex.getMessage()
        );
    }

    @Test
    void unifiedAgentBeanMethodShouldRegisterAllUnifiedToolsWhenToolBeansProvided() throws Exception {
        UnifiedAgentConfig config = new UnifiedAgentConfig();
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setToolResultOffloadEnabled(false);
        properties.getChatMemory().setToolResultRehydrateEnabled(false);

        AiSessionSandboxGateway sessionSandboxGateway = mock(AiSessionSandboxGateway.class);
        BrowserTool browserTool = new BrowserTool(
                sessionSandboxGateway,
                mock(AiSearchConfig.class),
                mock(AiProxyConfig.class)
        );
        PythonTool pythonTool = new PythonTool(mock(AiCodeSandbox.class), sessionSandboxGateway);
        FileTool fileTool = new FileTool(sessionSandboxGateway);
        ReflectionTool reflectionTool = new ReflectionTool();

        UnifiedAgent agent = config.unifiedAgent(
                mock(AiChatModel.class),
                mock(AiMemoryProvider.class),
                properties,
                null,
                browserTool,
                pythonTool,
                fileTool,
                reflectionTool
        );

        @SuppressWarnings("unchecked")
        Map<String, AiRegisteredTool> tools = (Map<String, AiRegisteredTool>) readExecutorField(agent, "tools");
        assertFalse(tools.isEmpty());
        assertEquals(true, tools.containsKey("searchWeb"));
        assertEquals(true, tools.containsKey("browseWeb"));
        assertEquals(true, tools.containsKey("executePython"));
        assertEquals(true, tools.containsKey("executePythonFile"));
        assertEquals(true, tools.containsKey("readFile"));
        assertEquals(true, tools.containsKey("writeFile"));
        assertEquals(true, tools.containsKey("recordTask"));
        assertEquals(true, tools.containsKey("reflectOnTask"));
    }

    @Test
    void unifiedAgentBeanMethodShouldAllowNullToolsWithoutRegistration() throws Exception {
        UnifiedAgentConfig config = new UnifiedAgentConfig();
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setToolResultOffloadEnabled(false);
        properties.getChatMemory().setToolResultRehydrateEnabled(false);

        UnifiedAgent agent = config.unifiedAgent(
                mock(AiChatModel.class),
                mock(AiMemoryProvider.class),
                properties,
                null,
                null,
                null,
                null,
                null
        );

        @SuppressWarnings("unchecked")
        Map<String, AiRegisteredTool> tools = (Map<String, AiRegisteredTool>) readExecutorField(agent, "tools");
        assertEquals(0, tools.size());
    }

    @Test
    void unifiedAgentBeanMethodShouldKeepMcpResourceReadOutsideCurrentDefaultToolchain() throws Exception {
        UnifiedAgentConfig config = new UnifiedAgentConfig();
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setToolResultOffloadEnabled(false);
        properties.getChatMemory().setToolResultRehydrateEnabled(false);
        properties.getMcp().setEnabled(true);

        McpToolRegistryBootstrap bootstrap = new McpToolRegistryBootstrap(
                new StubMcpClient(),
                new McpToolSpecAdapter()
        );

        UnifiedAgent agent = config.unifiedAgent(
                mock(AiChatModel.class),
                mock(AiMemoryProvider.class),
                properties,
                null,
                bootstrap,
                null,
                null,
                null,
                null
        );

        @SuppressWarnings("unchecked")
        Map<String, AiRegisteredTool> tools = (Map<String, AiRegisteredTool>) readExecutorField(agent, "tools");
        assertEquals(false, tools.containsKey("mcp.resource.read"));
    }

    @Test
    void unifiedAgentBeanMethodShouldKeepToolSetUnchangedWhenBootstrapMissing() throws Exception {
        UnifiedAgentConfig config = new UnifiedAgentConfig();
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setToolResultOffloadEnabled(false);
        properties.getChatMemory().setToolResultRehydrateEnabled(false);

        UnifiedAgent agent = config.unifiedAgent(
                mock(AiChatModel.class),
                mock(AiMemoryProvider.class),
                properties,
                null,
                null,
                null,
                null,
                null
        );

        @SuppressWarnings("unchecked")
        Map<String, AiRegisteredTool> tools = (Map<String, AiRegisteredTool>) readExecutorField(agent, "tools");
        assertEquals(false, tools.containsKey("mcp.resource.read"));
    }

    @Test
    void unifiedAgentBeanMethodShouldFailFastWhenMcpAndLocalToolsShareSameName() {
        UnifiedAgentConfig config = new UnifiedAgentConfig();
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setToolResultOffloadEnabled(false);
        properties.getChatMemory().setToolResultRehydrateEnabled(false);
        properties.getMcp().setEnabled(true);

        AiSessionSandboxGateway sessionSandboxGateway = mock(AiSessionSandboxGateway.class);
        FileTool fileTool = new FileTool(sessionSandboxGateway);
        McpToolRegistryBootstrap bootstrap = new McpToolRegistryBootstrap(
                new ConflictingNameMcpClient(),
                new McpToolSpecAdapter()
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> config.unifiedAgent(
                        mock(AiChatModel.class),
                        mock(AiMemoryProvider.class),
                        properties,
                        null,
                        bootstrap,
                        null,
                        null,
                        fileTool,
                        null
                )
        );
        assertTrue(ex.getMessage().contains("Duplicate tool name detected: readFile"));
    }

    @Test
    void unifiedAgentBeanMethodShouldFailFastWhenMultipleMcpToolNamesConflictWithLocalTools() {
        UnifiedAgentConfig config = new UnifiedAgentConfig();
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setToolResultOffloadEnabled(false);
        properties.getChatMemory().setToolResultRehydrateEnabled(false);
        properties.getMcp().setEnabled(true);

        AiSessionSandboxGateway sessionSandboxGateway = mock(AiSessionSandboxGateway.class);
        FileTool fileTool = new FileTool(sessionSandboxGateway);
        McpToolRegistryBootstrap bootstrap = new McpToolRegistryBootstrap(
                new MultiConflictingNameMcpClient(),
                new McpToolSpecAdapter()
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> config.unifiedAgent(
                        mock(AiChatModel.class),
                        mock(AiMemoryProvider.class),
                        properties,
                        null,
                        bootstrap,
                        null,
                        null,
                        fileTool,
                        null
                )
        );
        assertEquals("Duplicate tool names detected: readFile, writeFile", ex.getMessage());
    }

    private static Object readExecutorField(UnifiedAgent agent, String fieldName) throws Exception {
        Field field = AbstractAgentExecutor.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(agent);
    }

    private static Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static final class StubMcpClient implements McpClient {

        @Override
        public List<McpToolDefinition> discoverTools() {
            return List.of(new McpToolDefinition("mcp.echo", "echo", null));
        }

        @Override
        public McpToolResult invoke(McpInvokeRequest request) {
            return new McpToolResult(request.toolName(), "ok", false);
        }
    }

    private static final class ConflictingNameMcpClient implements McpClient {

        @Override
        public List<McpToolDefinition> discoverTools() {
            return List.of(new McpToolDefinition("readFile", "mcp duplicate", null));
        }

        @Override
        public McpToolResult invoke(McpInvokeRequest request) {
            throw new UnsupportedOperationException("not used in duplicate-name guard test");
        }

        @Override
        public List<McpResourceDefinition> discoverResources() {
            return List.of();
        }
    }

    private static final class MultiConflictingNameMcpClient implements McpClient {

        @Override
        public List<McpToolDefinition> discoverTools() {
            return List.of(
                    new McpToolDefinition("readFile", "mcp duplicate read", null),
                    new McpToolDefinition("writeFile", "mcp duplicate write", null)
            );
        }

        @Override
        public McpToolResult invoke(McpInvokeRequest request) {
            throw new UnsupportedOperationException("not used in duplicate-name guard test");
        }

        @Override
        public List<McpResourceDefinition> discoverResources() {
            return List.of();
        }
    }

}
