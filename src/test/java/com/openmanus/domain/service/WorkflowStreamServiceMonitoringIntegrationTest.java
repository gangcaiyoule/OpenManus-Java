package com.openmanus.domain.service;

import com.openmanus.infra.web.AgentMonitoringController;
import com.openmanus.domain.model.DetailedExecutionFlow;
import com.openmanus.infra.monitoring.AgentExecutionTracker;
import com.openmanus.infra.monitoring.ExecutionMonitoringServiceAdapter;
import com.openmanus.infra.monitoring.WorkflowExecutionEventPortAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WorkflowStreamServiceMonitoringIntegrationTest {

    @Test
    void shouldExposeDetailedFlowAfterSuccessfulWorkflowExecution() {
        AgentExecutionTracker tracker = new AgentExecutionTracker();
        WorkflowExecutionPort workflowExecutionPort = new WorkflowExecutionPort() {
            @Override
            public CompletableFuture<String> execute(String userInput, String conversationId) {
                return CompletableFuture.completedFuture("done:" + userInput);
            }

            @Override
            public String executeSync(String userInput, String conversationId) {
                return "done:" + userInput;
            }
        };
        WorkflowExecutionEventPort executionEventPort = new WorkflowExecutionEventPortAdapter(tracker);
        WorkflowStreamPublisher streamPublisher = new NoOpWorkflowStreamPublisher();
        Executor directExecutor = Runnable::run;
        WorkflowStreamService workflowStreamService = new WorkflowStreamService(
                workflowExecutionPort,
                executionEventPort,
                streamPublisher,
                directExecutor
        );
        AgentMonitoringController controller =
                new AgentMonitoringController(new ExecutionMonitoringServiceAdapter(tracker));

        workflowStreamService.executeWorkflowInternal("task", "session-123", event -> { });

        ResponseEntity<DetailedExecutionFlow> response = controller.getDetailedExecutionFlow("session-123");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("session-123", response.getBody().getSessionId());
        assertEquals("task", response.getBody().getUserInput());
        assertEquals("done:task", response.getBody().getFinalResult());
        assertEquals(DetailedExecutionFlow.WorkflowStatus.COMPLETED, response.getBody().getStatus());
        assertNotNull(response.getBody().getStartTime());
        assertNotNull(response.getBody().getEndTime());
        assertNotNull(response.getBody().getTotalDuration());
    }

    private static final class NoOpWorkflowStreamPublisher implements WorkflowStreamPublisher {

        @Override
        public void publishEvent(String sessionId, com.openmanus.domain.model.AgentExecutionEvent event) {
        }

        @Override
        public void publishResult(String sessionId, com.openmanus.domain.model.WorkflowResultVO result) {
        }
    }
}
