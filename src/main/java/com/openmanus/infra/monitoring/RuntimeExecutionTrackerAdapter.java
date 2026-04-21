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

    private static AgentExecutionEvent.ExecutionStatus toDomainStatus(AiExecutionStatus status) {
        if (status == null) {
            return AgentExecutionEvent.ExecutionStatus.ERROR;
        }
        return switch (status) {
            case PENDING -> AgentExecutionEvent.ExecutionStatus.PENDING;
            case RUNNING -> AgentExecutionEvent.ExecutionStatus.RUNNING;
            case SUCCESS -> AgentExecutionEvent.ExecutionStatus.SUCCESS;
            case FAILED -> AgentExecutionEvent.ExecutionStatus.FAILED;
            case CANCELLED -> AgentExecutionEvent.ExecutionStatus.CANCELLED;
            case ERROR -> AgentExecutionEvent.ExecutionStatus.ERROR;
            case TIMEOUT -> AgentExecutionEvent.ExecutionStatus.TIMEOUT;
        };
    }

    private static AiExecutionStatus toRuntimeStatus(AgentExecutionEvent.ExecutionStatus status) {
        if (status == null) {
            return AiExecutionStatus.ERROR;
        }
        return switch (status) {
            case PENDING -> AiExecutionStatus.PENDING;
            case RUNNING -> AiExecutionStatus.RUNNING;
            case SUCCESS -> AiExecutionStatus.SUCCESS;
            case FAILED -> AiExecutionStatus.FAILED;
            case CANCELLED -> AiExecutionStatus.CANCELLED;
            case ERROR -> AiExecutionStatus.ERROR;
            case TIMEOUT -> AiExecutionStatus.TIMEOUT;
        };
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
