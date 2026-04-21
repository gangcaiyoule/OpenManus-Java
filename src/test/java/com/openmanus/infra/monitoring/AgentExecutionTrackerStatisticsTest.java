package com.openmanus.infra.monitoring;

import com.openmanus.domain.model.AgentExecutionEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentExecutionTrackerStatisticsTest {

    @Test
    void shouldCountFailedErrorAndTimeoutStatusesAsErrors() {
        AgentExecutionTracker tracker = new AgentExecutionTracker();

        tracker.recordCustomEvent(event("session-1", "agent-a", AgentExecutionEvent.ExecutionStatus.SUCCESS));
        tracker.recordCustomEvent(event("session-1", "agent-a", AgentExecutionEvent.ExecutionStatus.FAILED));
        tracker.recordCustomEvent(event("session-1", "agent-b", AgentExecutionEvent.ExecutionStatus.ERROR));
        tracker.recordCustomEvent(event("session-1", "agent-c", AgentExecutionEvent.ExecutionStatus.TIMEOUT));

        Map<String, Object> stats = tracker.getSessionStatistics("session-1");

        assertEquals(4, stats.get("totalEvents"));
        assertEquals(3L, stats.get("errorCount"));
        assertEquals(1L, stats.get("successCount"));
        assertEquals(3L, stats.get("agentCount"));
        assertEquals(25.0d, stats.get("successRate"));
    }

    @Test
    void shouldIgnoreAgentStartEventWhenCalculatingSuccessRate() {
        AgentExecutionTracker tracker = new AgentExecutionTracker();

        tracker.recordCustomEvent(event(
                "session-start-success",
                "agent-a",
                AgentExecutionEvent.EventType.AGENT_START,
                AgentExecutionEvent.ExecutionStatus.RUNNING
        ));
        tracker.recordCustomEvent(event(
                "session-start-success",
                "agent-a",
                AgentExecutionEvent.EventType.AGENT_END,
                AgentExecutionEvent.ExecutionStatus.SUCCESS
        ));

        Map<String, Object> stats = tracker.getSessionStatistics("session-start-success");

        assertEquals(2, stats.get("totalEvents"));
        assertEquals(1L, stats.get("successCount"));
        assertEquals(100.0d, stats.get("successRate"));
    }

    @Test
    void shouldIgnoreToolSuccessEventsWhenCalculatingSuccessCount() {
        AgentExecutionTracker tracker = new AgentExecutionTracker();

        tracker.recordCustomEvent(event(
                "session-tool-success",
                "agent-a",
                AgentExecutionEvent.EventType.TOOL_CALL_END,
                AgentExecutionEvent.ExecutionStatus.SUCCESS
        ));
        tracker.recordCustomEvent(event(
                "session-tool-success",
                "agent-a",
                AgentExecutionEvent.EventType.AGENT_END,
                AgentExecutionEvent.ExecutionStatus.SUCCESS
        ));

        Map<String, Object> stats = tracker.getSessionStatistics("session-tool-success");

        assertEquals(2, stats.get("totalEvents"));
        assertEquals(1L, stats.get("successCount"));
        assertEquals(100.0d, stats.get("successRate"));
    }

    @Test
    void shouldCalculateSuccessRateUsingOnlyAgentEndEvents() {
        AgentExecutionTracker tracker = new AgentExecutionTracker();

        tracker.recordCustomEvent(event(
                "session-2",
                "agent-a",
                AgentExecutionEvent.EventType.AGENT_START,
                AgentExecutionEvent.ExecutionStatus.RUNNING
        ));
        tracker.recordCustomEvent(event(
                "session-2",
                "agent-a",
                AgentExecutionEvent.EventType.ERROR,
                AgentExecutionEvent.ExecutionStatus.FAILED
        ));
        tracker.recordCustomEvent(event(
                "session-2",
                "agent-a",
                AgentExecutionEvent.EventType.AGENT_END,
                AgentExecutionEvent.ExecutionStatus.ERROR
        ));

        Map<String, Object> stats = tracker.getSessionStatistics("session-2");

        assertEquals(3, stats.get("totalEvents"));
        assertEquals(2L, stats.get("errorCount"));
        assertEquals(0L, stats.get("successCount"));
        assertEquals(0.0d, stats.get("successRate"));
    }

    private static AgentExecutionEvent event(String sessionId, String agentName,
                                             AgentExecutionEvent.ExecutionStatus status) {
        return event(sessionId, agentName, AgentExecutionEvent.EventType.AGENT_END, status);
    }

    private static AgentExecutionEvent event(String sessionId, String agentName,
                                             AgentExecutionEvent.EventType eventType,
                                             AgentExecutionEvent.ExecutionStatus status) {
        return AgentExecutionEvent.builder()
                .sessionId(sessionId)
                .agentName(agentName)
                .agentType("WORKFLOW")
                .eventType(eventType)
                .status(status)
                .build();
    }
}
