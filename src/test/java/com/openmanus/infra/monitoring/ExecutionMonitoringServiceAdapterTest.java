package com.openmanus.infra.monitoring;

import com.openmanus.domain.model.AgentExecutionEvent;
import com.openmanus.domain.model.DetailedExecutionFlow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionMonitoringServiceAdapterTest {

    @Test
    void shouldDelegateAllQueriesAndCleanup() {
        AgentExecutionTracker tracker = mock(AgentExecutionTracker.class);
        ExecutionMonitoringServiceAdapter adapter = new ExecutionMonitoringServiceAdapter(tracker);

        Map<String, AgentExecutionEvent> active = Map.of("s1", AgentExecutionEvent.builder().build());
        List<AgentExecutionEvent> events = List.of(AgentExecutionEvent.builder().build());
        DetailedExecutionFlow flow = DetailedExecutionFlow.builder().sessionId("s1").build();
        Map<String, DetailedExecutionFlow> flows = Map.of("s1", flow);
        Map<String, Object> stats = Map.of("totalEvents", 1);

        when(tracker.getAllActiveSessions()).thenReturn(active);
        when(tracker.getSessionEvents("s1")).thenReturn(events);
        when(tracker.getDetailedExecutionFlow("s1")).thenReturn(flow);
        when(tracker.getAllDetailedExecutionFlows()).thenReturn(flows);
        when(tracker.getSessionStatistics("s1")).thenReturn(stats);

        assertSame(active, adapter.getAllActiveSessions());
        assertSame(events, adapter.getSessionEvents("s1"));
        assertSame(flow, adapter.getDetailedExecutionFlow("s1"));
        assertSame(flows, adapter.getAllDetailedExecutionFlows());
        assertSame(stats, adapter.getSessionStatistics("s1"));
        adapter.cleanupCompletedFlows(24);

        verify(tracker).cleanupCompletedFlows(24);
    }
}
