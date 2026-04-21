package com.openmanus.infra.monitoring;

import com.openmanus.domain.model.AgentExecutionEvent;
import com.openmanus.domain.model.WorkflowResultVO;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WebSocketWorkflowStreamPublisherTest {

    @Test
    void shouldPublishEventAndResultToSessionScopedTopics() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        WebSocketWorkflowStreamPublisher publisher = new WebSocketWorkflowStreamPublisher(messagingTemplate);
        AgentExecutionEvent event = AgentExecutionEvent.builder().sessionId("session-1").build();
        WorkflowResultVO result = WorkflowResultVO.builder().sessionId("session-1").build();

        publisher.publishEvent("session-1", event);
        publisher.publishResult("session-1", result);

        verify(messagingTemplate).convertAndSend("/topic/executions/session-1", event);
        verify(messagingTemplate).convertAndSend("/topic/executions/session-1/result", result);
    }
}
