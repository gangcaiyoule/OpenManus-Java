package com.openmanus.infra.monitoring;

import com.openmanus.aiframework.runtime.AiExecutionEvent;
import com.openmanus.aiframework.runtime.AiExecutionStatus;
import com.openmanus.aiframework.runtime.AiExecutionTracker;
import com.openmanus.domain.model.AgentExecutionEvent;
import java.util.Objects;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Infra adapter exposing AgentExecutionTracker via runtime tracker port.
 */
@Component
public class RuntimeExecutionTrackerAdapter implements AiExecutionTracker {

    private final AgentExecutionTracker delegate;
    private final Map<ListenerKey, AgentExecutionTracker.AgentExecutionEventListener> listeners =
            new ConcurrentHashMap<>();

    public RuntimeExecutionTrackerAdapter(AgentExecutionTracker delegate) {
        this.delegate = delegate;
    }

    @Override
    public void startAgentExecution(String sessionId, String agentName, String agentType, Object input) {
        delegate.startAgentExecution(sessionId, agentName, agentType, input);
    }

    @Override
    public void endAgentExecution(String sessionId, String agentName, String agentType, Object output,
                                  AiExecutionStatus status) {
        delegate.endAgentExecution(sessionId, agentName, agentType, output, toDomainStatus(status));
    }

    @Override
    public void recordAgentError(String sessionId, String agentName, String agentType, String error) {
        delegate.recordAgentError(sessionId, agentName, agentType, error);
    }

    @Override
    public void addListener(String sessionId, AgentExecutionEventListener listener) {
        AgentExecutionTracker.AgentExecutionEventListener delegateListener =
                event -> listener.onEvent(toRuntimeEvent(event));
        ListenerKey key = new ListenerKey(sessionId, listener);
        AgentExecutionTracker.AgentExecutionEventListener existing =
                listeners.putIfAbsent(key, delegateListener);
        if (existing == null) {
            delegate.addListener(sessionId, delegateListener);
        }
    }

    @Override
    public void removeListener(String sessionId, AgentExecutionEventListener listener) {
        AgentExecutionTracker.AgentExecutionEventListener delegateListener =
                listeners.remove(new ListenerKey(sessionId, listener));
        if (delegateListener != null) {
            delegate.removeListener(sessionId, delegateListener);
        }
    }

    private record ListenerKey(String sessionId, AgentExecutionEventListener listener) {

        private ListenerKey {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(listener, "listener");
        }
    }

    private static String toDomainStatus(AiExecutionStatus status) {
        if (status == null) {
            return "ERROR";
        }
        return status.name();
    }

    private static AiExecutionStatus toRuntimeStatus(String status) {
        if (status == null) {
            return AiExecutionStatus.ERROR;
        }
        try {
            return AiExecutionStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return AiExecutionStatus.ERROR;
        }
    }

    private static AiExecutionEvent toRuntimeEvent(AgentExecutionEvent event) {
        if (event == null) {
            return null;
        }
        return new AiExecutionEvent(
                event.getSessionId(),
                event.getEventId(),
                event.getAgentName(),
                event.getAgentType(),
                event.getEventType() == null ? null : event.getEventType().name(),
                toRuntimeStatus(event.getStatus()),
                event.getInput(),
                event.getOutput(),
                event.getError(),
                event.getStartTime(),
                event.getEndTime(),
                event.getDuration(),
                event.getMetadata()
        );
    }
}
