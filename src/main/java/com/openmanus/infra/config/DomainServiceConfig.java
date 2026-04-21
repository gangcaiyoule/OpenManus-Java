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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.concurrent.Executor;

@Configuration
public class DomainServiceConfig {

    @Bean
    AgentService agentService(WorkflowExecutionPort workflowExecutionPort,
                              WorkflowExecutionEventPort executionEventPort) {
        return new AgentService(workflowExecutionPort, executionEventPort);
    }

    @Bean
    WorkflowStreamService workflowStreamService(WorkflowExecutionPort workflowExecutionPort,
                                                WorkflowExecutionEventPort executionEventPort,
                                                WorkflowStreamPublisher streamPublisher,
                                                @Qualifier(AsyncConfig.ASYNC_EXECUTOR_NAME) Executor asyncExecutor) {
        return new WorkflowStreamService(
                workflowExecutionPort,
                executionEventPort,
                streamPublisher,
                asyncExecutor
        );
    }

    @Bean
    SessionSandboxManager sessionSandboxManager(SessionSandboxClient sessionSandboxClient,
                                                SessionFileSandboxDirectoryProvider fileSandboxDirectoryProvider) {
        return new SessionSandboxManager(sessionSandboxClient, fileSandboxDirectoryProvider);
    }

    @Bean
    WebProxyService webProxyService(WebProxyFetchPort webProxyFetchPort) {
        return new WebProxyService(webProxyFetchPort);
    }
}
