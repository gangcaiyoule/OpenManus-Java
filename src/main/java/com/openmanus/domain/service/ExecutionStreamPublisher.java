package com.openmanus.domain.service;

import com.openmanus.domain.model.AgentExecutionEvent;
import com.openmanus.domain.model.ExecutionResultView;

public interface ExecutionStreamPublisher {

    void publishEvent(String topic, AgentExecutionEvent event);

    void publishResult(String topic, ExecutionResultView result);
}
