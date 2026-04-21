package com.openmanus.infra.config;

import com.openmanus.agent.tool.BrowserTool;
import com.openmanus.agent.tool.FileTool;
import com.openmanus.agent.tool.PythonTool;
import com.openmanus.agent.tool.ReflectionTool;
import com.openmanus.agent.tool.support.SandboxPathResolver;
import com.openmanus.agent.workflow.UnifiedWorkflow;
import com.openmanus.agent.impl.unified.UnifiedAgent;
import com.openmanus.aiframework.runtime.AiCodeSandbox;
import com.openmanus.aiframework.runtime.AiProxyConfig;
import com.openmanus.aiframework.runtime.AiSearchConfig;
import com.openmanus.aiframework.runtime.AiSessionSandboxGateway;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class UnifiedAgentConfigBeanWiringTest {

    private final UnifiedAgentConfig config = new UnifiedAgentConfig();

    @Test
    void shouldCreateToolAndWorkflowBeansFromRuntimePorts() throws Exception {
        AiSessionSandboxGateway sessionSandboxGateway = mock(AiSessionSandboxGateway.class);
        AiSearchConfig searchConfig = mock(AiSearchConfig.class);
        AiProxyConfig proxyConfig = mock(AiProxyConfig.class);
        AiCodeSandbox sandbox = mock(AiCodeSandbox.class);
        UnifiedAgent unifiedAgent = mock(UnifiedAgent.class);
        Executor executor = Runnable::run;

        SandboxPathResolver pathResolver = config.sandboxPathResolver(sessionSandboxGateway);
        BrowserTool browserTool = config.browserTool(sessionSandboxGateway, searchConfig, proxyConfig);
        PythonTool pythonTool = config.pythonTool(sandbox, pathResolver);
        FileTool fileTool = config.fileTool(pathResolver);
        ReflectionTool reflectionTool = config.reflectionTool();
        UnifiedWorkflow workflow = config.unifiedWorkflow(unifiedAgent, executor);

        assertNotNull(browserTool);
        assertNotNull(pythonTool);
        assertNotNull(fileTool);
        assertNotNull(reflectionTool);
        assertSame(sessionSandboxGateway, readField(pathResolver, "sessionSandboxGateway"));
        assertSame(sessionSandboxGateway, readField(browserTool, "sessionSandboxGateway"));
        assertSame(searchConfig, readField(browserTool, "searchConfig"));
        assertSame(proxyConfig, readField(browserTool, "proxyConfig"));
        assertSame(sandbox, readField(pythonTool, "sandbox"));
        assertSame(pathResolver, readField(pythonTool, "sandboxPathResolver"));
        assertSame(pathResolver, readField(fileTool, "sandboxPathResolver"));
        assertSame(unifiedAgent, readField(workflow, "unifiedAgent"));
        assertSame(executor, readField(workflow, "asyncExecutor"));
    }

    private static Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
