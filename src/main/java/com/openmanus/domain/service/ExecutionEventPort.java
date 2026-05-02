package com.openmanus.domain.service;

import com.openmanus.domain.model.AgentExecutionEvent;

public interface ExecutionEventPort {

    void startExecutionTracking(String sessionId, String userInput);

    void endExecutionTracking(String sessionId, String finalResult, boolean success);

    void startExecution(String sessionId, String agentName, String agentType, Object input);

    void endExecution(String sessionId, String agentName, String agentType, Object output,
                      String status);

    void recordError(String sessionId, String agentName, String agentType, String error);

    void recordCustomEvent(AgentExecutionEvent event);

    void addListener(String sessionId, Listener listener);

    void removeListener(String sessionId, Listener listener);

    interface Listener {
        void onEvent(AgentExecutionEvent event);
    }
}
