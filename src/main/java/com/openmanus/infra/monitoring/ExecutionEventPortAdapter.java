package com.openmanus.infra.monitoring;

import com.openmanus.domain.model.AgentExecutionEvent;
import com.openmanus.domain.service.ExecutionEventPort;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ExecutionEventPortAdapter implements ExecutionEventPort {

    private final AgentExecutionTracker tracker;
    private final Map<ListenerKey, AgentExecutionTracker.AgentExecutionEventListener> listeners =
            new ConcurrentHashMap<>();

    public ExecutionEventPortAdapter(AgentExecutionTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public void startExecutionTracking(String sessionId, String userInput) {
        tracker.startExecutionTracking(sessionId, userInput);
    }

    @Override
    public void endExecutionTracking(String sessionId, String finalResult, boolean success) {
        tracker.endExecutionTracking(sessionId, finalResult, success);
    }

    @Override
    public void startExecution(String sessionId, String agentName, String agentType, Object input) {
        tracker.startAgentExecution(sessionId, agentName, agentType, input);
    }

    @Override
    public void endExecution(String sessionId, String agentName, String agentType, Object output,
                             String status) {
        tracker.endAgentExecution(sessionId, agentName, agentType, output, status);
    }

    @Override
    public void recordError(String sessionId, String agentName, String agentType, String error) {
        tracker.recordAgentError(sessionId, agentName, agentType, error);
    }

    @Override
    public void recordCustomEvent(AgentExecutionEvent event) {
        tracker.recordCustomEvent(event);
    }

    @Override
    public void addListener(String sessionId, Listener listener) {
        AgentExecutionTracker.AgentExecutionEventListener delegateListener = listener::onEvent;
        ListenerKey listenerKey = new ListenerKey(sessionId, listener);
        AgentExecutionTracker.AgentExecutionEventListener existing =
                listeners.putIfAbsent(listenerKey, delegateListener);
        if (existing == null) {
            try {
                tracker.addListener(sessionId, delegateListener);
            } catch (RuntimeException ex) {
                listeners.remove(listenerKey, delegateListener);
                throw ex;
            }
        }
    }

    @Override
    public void removeListener(String sessionId, Listener listener) {
        AgentExecutionTracker.AgentExecutionEventListener delegateListener =
                listeners.remove(new ListenerKey(sessionId, listener));
        if (delegateListener != null) {
            tracker.removeListener(sessionId, delegateListener);
        }
    }

    private record ListenerKey(String sessionId, Listener listener) {

        private ListenerKey {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(listener, "listener");
        }
    }
}
