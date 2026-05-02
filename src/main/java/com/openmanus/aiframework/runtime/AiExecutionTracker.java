package com.openmanus.aiframework.runtime;

/**
 * Runtime abstraction for agent execution tracking and event subscription.
 */
public interface AiExecutionTracker {

    void startAgentExecution(String sessionId, String agentName, String agentType, Object input);

    void endAgentExecution(
            String sessionId,
            String agentName,
            String agentType,
            Object output,
            AiExecutionStatus status
    );

    void recordAgentError(String sessionId, String agentName, String agentType, String error);

    void addListener(String sessionId, AgentExecutionEventListener listener);

    void removeListener(String sessionId, AgentExecutionEventListener listener);

    interface AgentExecutionEventListener {
        void onEvent(AiExecutionEvent event);
    }
}
