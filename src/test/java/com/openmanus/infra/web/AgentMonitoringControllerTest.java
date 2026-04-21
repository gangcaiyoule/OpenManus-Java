package com.openmanus.infra.web;

import com.openmanus.domain.model.AgentExecutionEvent;
import com.openmanus.domain.model.DetailedExecutionFlow;
import com.openmanus.domain.service.ExecutionMonitoringService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentMonitoringControllerTest {

    @Test
    void shouldBuildDashboardCountersFromMonitoringService() {
        ExecutionMonitoringService monitoringService = mock(ExecutionMonitoringService.class);
        AgentMonitoringController controller = new AgentMonitoringController(monitoringService);
        Map<String, AgentExecutionEvent> activeSessions = Map.of(
                "s1", AgentExecutionEvent.builder().sessionId("s1").build(),
                "s2", AgentExecutionEvent.builder().sessionId("s2").build()
        );
        Map<String, DetailedExecutionFlow> allFlows = Map.of(
                "f1", flow("f1", DetailedExecutionFlow.WorkflowStatus.RUNNING, 3),
                "f2", flow("f2", DetailedExecutionFlow.WorkflowStatus.COMPLETED, 2),
                "f3", flow("f3", DetailedExecutionFlow.WorkflowStatus.FAILED, 1)
        );
        when(monitoringService.getAllActiveSessions()).thenReturn(activeSessions);
        when(monitoringService.getAllDetailedExecutionFlows()).thenReturn(allFlows);

        ResponseEntity<Map<String, Object>> response = controller.getDashboard();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().get("totalActiveSessions"));
        assertEquals(3, response.getBody().get("totalDetailedFlows"));
        assertEquals(1L, response.getBody().get("runningFlows"));
        assertEquals(1L, response.getBody().get("completedFlows"));
        assertEquals(1L, response.getBody().get("failedFlows"));
    }

    @Test
    void shouldIgnoreNullAndUnknownStatusFlowsWhenBuildingDashboardCounters() {
        ExecutionMonitoringService monitoringService = mock(ExecutionMonitoringService.class);
        AgentMonitoringController controller = new AgentMonitoringController(monitoringService);
        Map<String, DetailedExecutionFlow> allFlows = new java.util.LinkedHashMap<>();
        allFlows.put("running", flow("running", DetailedExecutionFlow.WorkflowStatus.RUNNING, 3));
        allFlows.put("missing-status", DetailedExecutionFlow.builder()
                .sessionId("missing-status")
                .startTime(LocalDateTime.of(2026, 4, 3, 2, 0))
                .build());
        allFlows.put("null-flow", null);
        when(monitoringService.getAllActiveSessions()).thenReturn(Map.of());
        when(monitoringService.getAllDetailedExecutionFlows()).thenReturn(allFlows);

        ResponseEntity<Map<String, Object>> response = controller.getDashboard();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().get("totalDetailedFlows"));
        assertEquals(1L, response.getBody().get("runningFlows"));
        assertEquals(0L, response.getBody().get("completedFlows"));
        assertEquals(0L, response.getBody().get("failedFlows"));
    }

    @Test
    void shouldReturn500WhenDashboardLoadingFails() {
        ExecutionMonitoringService monitoringService = mock(ExecutionMonitoringService.class);
        AgentMonitoringController controller = new AgentMonitoringController(monitoringService);
        when(monitoringService.getAllActiveSessions()).thenThrow(new IllegalStateException("boom"));

        ResponseEntity<Map<String, Object>> response = controller.getDashboard();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void shouldReturnNotFoundForMissingDetailedFlow() {
        ExecutionMonitoringService monitoringService = mock(ExecutionMonitoringService.class);
        AgentMonitoringController controller = new AgentMonitoringController(monitoringService);
        when(monitoringService.getDetailedExecutionFlow("s1")).thenReturn(null);

        ResponseEntity<DetailedExecutionFlow> response = controller.getDetailedExecutionFlow("s1");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void shouldSortAndLimitRecentFlows() {
        ExecutionMonitoringService monitoringService = mock(ExecutionMonitoringService.class);
        AgentMonitoringController controller = new AgentMonitoringController(monitoringService);
        Map<String, DetailedExecutionFlow> flows = Map.of(
                "old", flow("old", DetailedExecutionFlow.WorkflowStatus.COMPLETED, 1),
                "new", flow("new", DetailedExecutionFlow.WorkflowStatus.RUNNING, 5),
                "mid", flow("mid", DetailedExecutionFlow.WorkflowStatus.FAILED, 3)
        );
        when(monitoringService.getAllDetailedExecutionFlows()).thenReturn(flows);

        ResponseEntity<List<DetailedExecutionFlow>> response = controller.getRecentFlows(2);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertEquals("new", response.getBody().get(0).getSessionId());
        assertEquals("mid", response.getBody().get(1).getSessionId());
    }

    @Test
    void shouldIgnoreNullFlowsAndSortNullStartTimeLastForRecentFlows() {
        ExecutionMonitoringService monitoringService = mock(ExecutionMonitoringService.class);
        AgentMonitoringController controller = new AgentMonitoringController(monitoringService);
        Map<String, DetailedExecutionFlow> flows = new java.util.LinkedHashMap<>();
        flows.put("no-time", DetailedExecutionFlow.builder()
                .sessionId("no-time")
                .status(DetailedExecutionFlow.WorkflowStatus.RUNNING)
                .build());
        flows.put("new", flow("new", DetailedExecutionFlow.WorkflowStatus.RUNNING, 5));
        flows.put("null-flow", null);
        flows.put("old", flow("old", DetailedExecutionFlow.WorkflowStatus.COMPLETED, 1));
        when(monitoringService.getAllDetailedExecutionFlows()).thenReturn(flows);

        ResponseEntity<List<DetailedExecutionFlow>> response = controller.getRecentFlows(3);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().size());
        assertEquals("new", response.getBody().get(0).getSessionId());
        assertEquals("old", response.getBody().get(1).getSessionId());
        assertEquals("no-time", response.getBody().get(2).getSessionId());
    }

    @Test
    void shouldRejectNonPositiveRecentFlowLimit() {
        ExecutionMonitoringService monitoringService = mock(ExecutionMonitoringService.class);
        AgentMonitoringController controller = new AgentMonitoringController(monitoringService);

        ResponseEntity<List<DetailedExecutionFlow>> response = controller.getRecentFlows(0);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(monitoringService, never()).getAllDetailedExecutionFlows();
    }

    @Test
    void shouldHandleCleanupSuccessAndFailure() {
        ExecutionMonitoringService monitoringService = mock(ExecutionMonitoringService.class);
        AgentMonitoringController controller = new AgentMonitoringController(monitoringService);

        ResponseEntity<Map<String, Object>> ok = controller.cleanupFlows(48);
        assertEquals(HttpStatus.OK, ok.getStatusCode());
        assertTrue(Boolean.TRUE.equals(ok.getBody().get("success")));
        verify(monitoringService).cleanupCompletedFlows(48);

        doThrow(new RuntimeException("cleanup failed")).when(monitoringService).cleanupCompletedFlows(24);
        ResponseEntity<Map<String, Object>> failed = controller.cleanupFlows(24);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, failed.getStatusCode());
    }

    @Test
    void shouldRejectNonPositiveCleanupMaxAgeHours() {
        ExecutionMonitoringService monitoringService = mock(ExecutionMonitoringService.class);
        AgentMonitoringController controller = new AgentMonitoringController(monitoringService);

        ResponseEntity<Map<String, Object>> response = controller.cleanupFlows(-1);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(monitoringService, never()).cleanupCompletedFlows(-1);
    }

    @Test
    void shouldDelegateSessionAndFlowQueries() {
        ExecutionMonitoringService monitoringService = mock(ExecutionMonitoringService.class);
        AgentMonitoringController controller = new AgentMonitoringController(monitoringService);
        Map<String, AgentExecutionEvent> active = Map.of("s1", AgentExecutionEvent.builder().sessionId("s1").build());
        List<AgentExecutionEvent> events = List.of(AgentExecutionEvent.builder().sessionId("s1").build());
        DetailedExecutionFlow flow = flow("s1", DetailedExecutionFlow.WorkflowStatus.RUNNING, 1);
        Map<String, DetailedExecutionFlow> flows = Map.of("s1", flow);
        Map<String, Object> stats = Map.of("totalEvents", 1);
        when(monitoringService.getAllActiveSessions()).thenReturn(active);
        when(monitoringService.getSessionEvents("s1")).thenReturn(events);
        when(monitoringService.getDetailedExecutionFlow("s1")).thenReturn(flow);
        when(monitoringService.getAllDetailedExecutionFlows()).thenReturn(flows);
        when(monitoringService.getSessionStatistics("s1")).thenReturn(stats);

        assertSame(active, controller.getActiveSessions().getBody());
        assertSame(events, controller.getSessionEvents("s1").getBody());
        assertSame(flow, controller.getDetailedExecutionFlow("s1").getBody());
        assertSame(flows, controller.getAllDetailedFlows().getBody());
        assertSame(stats, controller.getSessionStats("s1").getBody());
    }

    @Test
    void shouldReturn500WhenSessionQueriesFail() {
        ExecutionMonitoringService monitoringService = mock(ExecutionMonitoringService.class);
        AgentMonitoringController controller = new AgentMonitoringController(monitoringService);
        doThrow(new IllegalStateException("events boom")).when(monitoringService).getSessionEvents("s1");
        when(monitoringService.getAllDetailedExecutionFlows()).thenThrow(new IllegalStateException("flows boom"));
        when(monitoringService.getSessionStatistics("s1")).thenThrow(new IllegalStateException("stats boom"));
        when(monitoringService.getAllActiveSessions()).thenThrow(new IllegalStateException("active boom"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, controller.getActiveSessions().getStatusCode());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, controller.getSessionEvents("s1").getStatusCode());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, controller.getAllDetailedFlows().getStatusCode());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, controller.getSessionStats("s1").getStatusCode());
    }

    private static DetailedExecutionFlow flow(String sessionId, DetailedExecutionFlow.WorkflowStatus status, int hour) {
        return DetailedExecutionFlow.builder()
                .sessionId(sessionId)
                .status(status)
                .startTime(LocalDateTime.of(2026, 4, 3, hour, 0))
                .build();
    }
}
