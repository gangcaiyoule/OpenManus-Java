package com.openmanus.infra.config;

import com.openmanus.domain.service.AgentService;
import com.openmanus.domain.service.SessionFileSandboxDirectoryProvider;
import com.openmanus.domain.service.SessionSandboxClient;
import com.openmanus.domain.service.SessionSandboxManager;
import com.openmanus.domain.service.WebProxyFetchPort;
import com.openmanus.domain.service.WebProxyService;
import com.openmanus.domain.service.WorkflowExecutionEventPort;
import com.openmanus.domain.service.WorkflowExecutionPort;
import com.openmanus.domain.service.WorkflowStreamPublisher;
import com.openmanus.domain.service.WorkflowStreamService;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class DomainServiceConfigTest {

    private final DomainServiceConfig config = new DomainServiceConfig();

    @Test
    void shouldCreateDomainServicesFromPortsAndAdapters() {
        WorkflowExecutionPort workflowExecutionPort = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        WorkflowStreamPublisher streamPublisher = mock(WorkflowStreamPublisher.class);
        Executor executor = Runnable::run;
        SessionSandboxClient sessionSandboxClient = mock(SessionSandboxClient.class);
        SessionFileSandboxDirectoryProvider directoryProvider = mock(SessionFileSandboxDirectoryProvider.class);
        WebProxyFetchPort webProxyFetchPort = mock(WebProxyFetchPort.class);

        AgentService agentService = config.agentService(workflowExecutionPort, executionEventPort);
        WorkflowStreamService workflowStreamService = config.workflowStreamService(
                workflowExecutionPort,
                executionEventPort,
                streamPublisher,
                executor
        );
        SessionSandboxManager sessionSandboxManager = config.sessionSandboxManager(
                sessionSandboxClient,
                directoryProvider
        );
        WebProxyService webProxyService = config.webProxyService(webProxyFetchPort);

        assertNotNull(agentService);
        assertNotNull(workflowStreamService);
        assertNotNull(sessionSandboxManager);
        assertNotNull(webProxyService);
    }

    @Test
    void shouldUseProvidedExecutorInstanceForWorkflowStreamService() {
        WorkflowExecutionPort workflowExecutionPort = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        WorkflowStreamPublisher streamPublisher = mock(WorkflowStreamPublisher.class);
        Executor executor = Runnable::run;

        WorkflowStreamService workflowStreamService = config.workflowStreamService(
                workflowExecutionPort,
                executionEventPort,
                streamPublisher,
                executor
        );

        assertSame(executor, readAsyncExecutor(workflowStreamService));
    }

    private static Executor readAsyncExecutor(WorkflowStreamService workflowStreamService) {
        try {
            var field = WorkflowStreamService.class.getDeclaredField("asyncExecutor");
            field.setAccessible(true);
            return (Executor) field.get(workflowStreamService);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("无法读取 WorkflowStreamService.asyncExecutor", e);
        }
    }
}
