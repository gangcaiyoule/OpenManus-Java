package com.openmanus.domain.service;

import com.openmanus.infra.web.AgentMonitoringController;
import com.openmanus.domain.model.DetailedExecutionFlow;
import com.openmanus.infra.monitoring.AgentExecutionTracker;
import com.openmanus.infra.monitoring.ExecutionMonitoringServiceAdapter;
import com.openmanus.infra.monitoring.WorkflowExecutionEventPortAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentServiceMonitoringIntegrationTest {

    @Test
    void shouldExposeDetailedFlowAfterSuccessfulChatExecution() {
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
        AgentService agentService = new AgentService(
                workflowExecutionPort,
                new WorkflowExecutionEventPortAdapter(tracker)
        );
        AgentMonitoringController controller =
                new AgentMonitoringController(new ExecutionMonitoringServiceAdapter(tracker));

        Map<String, Object> result = agentService.chat("task", "session-123", true).join();
        ResponseEntity<DetailedExecutionFlow> response = controller.getDetailedExecutionFlow("session-123");

        assertEquals("done:task", result.get("answer"));
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
}
