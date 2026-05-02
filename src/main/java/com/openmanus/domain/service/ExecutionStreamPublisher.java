package com.openmanus.domain.service;

import com.openmanus.domain.model.AgentExecutionEvent;
import com.openmanus.domain.model.ExecutionResultView;

public interface ExecutionStreamPublisher {

    void publishEvent(String sessionId, AgentExecutionEvent event);

    void publishResult(String sessionId, ExecutionResultView result);
}
