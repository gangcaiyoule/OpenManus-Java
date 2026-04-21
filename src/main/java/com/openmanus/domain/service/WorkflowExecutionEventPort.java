package com.openmanus.domain.service;

import com.openmanus.domain.model.AgentExecutionEvent;

public interface WorkflowExecutionEventPort {

    void startWorkflowTracking(String sessionId, String userInput);

    void endWorkflowTracking(String sessionId, String finalResult, boolean success);

    void startExecution(String sessionId, String agentName, String agentType, Object input);

    void endExecution(String sessionId, String agentName, String agentType, Object output,
                      AgentExecutionEvent.ExecutionStatus status);

    void recordError(String sessionId, String agentName, String agentType, String error);

    void addListener(String sessionId, Listener listener);

    void removeListener(String sessionId, Listener listener);

    interface Listener {
        void onEvent(AgentExecutionEvent event);
    }
}
