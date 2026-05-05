package com.openmanus.infra.monitoring;

import com.openmanus.domain.model.AgentExecutionEvent;
import com.openmanus.domain.model.DetailedExecutionFlow;
import com.openmanus.domain.service.ExecutionMonitoringService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Infra adapter that exposes monitoring tracker queries through domain port.
 */
@Component
public class ExecutionMonitoringServiceAdapter implements ExecutionMonitoringService {

    private final AgentExecutionTracker tracker;

    public ExecutionMonitoringServiceAdapter(AgentExecutionTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public Map<String, AgentExecutionEvent> getAllActiveSessions() {
        return tracker.getAllActiveSessions();
    }

    @Override
    public List<AgentExecutionEvent> getSessionEvents(String sessionId) {
        return tracker.getSessionEvents(sessionId);
    }

    @Override
    public DetailedExecutionFlow getDetailedExecutionFlow(String sessionId) {
        return tracker.getDetailedExecutionFlow(sessionId);
    }

    @Override
    public Map<String, DetailedExecutionFlow> getAllDetailedExecutionFlows() {
        return tracker.getAllDetailedExecutionFlows();
    }

    @Override
    public Map<String, Object> getSessionStatistics(String sessionId) {
        return tracker.getSessionStatistics(sessionId);
    }

    @Override
    public void cleanupCompletedFlows(int maxAgeHours) {
        tracker.cleanupCompletedFlows(maxAgeHours);
    }
}
