package com.openmanus.domain.service;

import com.openmanus.domain.model.AgentExecutionEvent;
import com.openmanus.domain.model.DetailedExecutionFlow;

import java.util.List;
import java.util.Map;

/**
 * Domain port for querying and managing execution monitoring data.
 */
public interface ExecutionMonitoringService {

    Map<String, AgentExecutionEvent> getAllActiveSessions();

    List<AgentExecutionEvent> getSessionEvents(String sessionId);

    DetailedExecutionFlow getDetailedExecutionFlow(String sessionId);

    Map<String, DetailedExecutionFlow> getAllDetailedExecutionFlows();

    Map<String, Object> getSessionStatistics(String sessionId);

    void cleanupCompletedFlows(int maxAgeHours);
}
