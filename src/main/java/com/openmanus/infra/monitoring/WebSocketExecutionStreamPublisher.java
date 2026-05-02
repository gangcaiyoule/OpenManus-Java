package com.openmanus.infra.monitoring;

import com.openmanus.domain.model.AgentExecutionEvent;
import com.openmanus.domain.model.ExecutionResultView;
import com.openmanus.domain.service.ExecutionStreamPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class WebSocketExecutionStreamPublisher implements ExecutionStreamPublisher {

    private static final String EXECUTION_TOPIC_PREFIX = "/topic/executions/";

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketExecutionStreamPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void publishEvent(String sessionId, AgentExecutionEvent event) {
        messagingTemplate.convertAndSend(EXECUTION_TOPIC_PREFIX + sessionId, event);
    }

    @Override
    public void publishResult(String sessionId, ExecutionResultView result) {
        messagingTemplate.convertAndSend(EXECUTION_TOPIC_PREFIX + sessionId + "/result", result);
    }
}
