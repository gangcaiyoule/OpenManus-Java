package com.openmanus.domain.service;

import com.openmanus.domain.model.AgentExecutionEvent;
import com.openmanus.domain.model.WorkflowResultVO;

public interface WorkflowStreamPublisher {

    void publishEvent(String sessionId, AgentExecutionEvent event);

    void publishResult(String sessionId, WorkflowResultVO result);
}
