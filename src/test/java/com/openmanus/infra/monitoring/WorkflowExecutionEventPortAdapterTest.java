package com.openmanus.infra.monitoring;

import com.openmanus.domain.model.AgentExecutionEvent;
import com.openmanus.domain.service.WorkflowExecutionEventPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class WorkflowExecutionEventPortAdapterTest {

    @Test
    void shouldDelegateLifecycleCallsToTracker() {
        AgentExecutionTracker tracker = mock(AgentExecutionTracker.class);
        WorkflowExecutionEventPortAdapter adapter = new WorkflowExecutionEventPortAdapter(tracker);

        adapter.startWorkflowTracking("session-1", "input");
        adapter.endWorkflowTracking("session-1", "output", true);
        adapter.startExecution("session-1", "agent", "START", "input");
        adapter.endExecution("session-1", "agent", "END", "output", AgentExecutionEvent.ExecutionStatus.SUCCESS);
        adapter.recordError("session-1", "agent", "END", "boom");

        verify(tracker).startWorkflowTracking("session-1", "input");
        verify(tracker).endWorkflowTracking("session-1", "output", true);
        verify(tracker).startAgentExecution("session-1", "agent", "START", "input");
        verify(tracker).endAgentExecution("session-1", "agent", "END", "output",
                AgentExecutionEvent.ExecutionStatus.SUCCESS);
        verify(tracker).recordAgentError("session-1", "agent", "END", "boom");
    }

    @Test
    void shouldReuseMappedListenerAndRemoveIt() {
        AgentExecutionTracker tracker = mock(AgentExecutionTracker.class);
        WorkflowExecutionEventPortAdapter adapter = new WorkflowExecutionEventPortAdapter(tracker);
        WorkflowExecutionEventPort.Listener listener = mock(WorkflowExecutionEventPort.Listener.class);

        adapter.addListener("session-1", listener);
        adapter.addListener("session-1", listener);

        ArgumentCaptor<AgentExecutionTracker.AgentExecutionEventListener> captor =
                ArgumentCaptor.forClass(AgentExecutionTracker.AgentExecutionEventListener.class);
        verify(tracker, times(1)).addListener(eq("session-1"), captor.capture());
        AgentExecutionTracker.AgentExecutionEventListener delegateListener = captor.getValue();

        AgentExecutionEvent event = AgentExecutionEvent.builder().sessionId("session-1").build();
        delegateListener.onEvent(event);
        verify(listener).onEvent(event);

        adapter.removeListener("session-1", listener);
        verify(tracker).removeListener("session-1", delegateListener);
    }

    @Test
    void shouldIgnoreUnknownListenerRemoval() {
        AgentExecutionTracker tracker = mock(AgentExecutionTracker.class);
        WorkflowExecutionEventPortAdapter adapter = new WorkflowExecutionEventPortAdapter(tracker);

        adapter.removeListener("session-1", mock(WorkflowExecutionEventPort.Listener.class));

        verify(tracker, never()).removeListener(eq("session-1"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldKeepSessionScopedListenerMappingsIndependent() {
        AgentExecutionTracker tracker = mock(AgentExecutionTracker.class);
        WorkflowExecutionEventPortAdapter adapter = new WorkflowExecutionEventPortAdapter(tracker);
        WorkflowExecutionEventPort.Listener listener = mock(WorkflowExecutionEventPort.Listener.class);

        adapter.addListener("session-1", listener);
        adapter.addListener("session-2", listener);

        ArgumentCaptor<AgentExecutionTracker.AgentExecutionEventListener> captor =
                ArgumentCaptor.forClass(AgentExecutionTracker.AgentExecutionEventListener.class);
        verify(tracker).addListener(eq("session-1"), captor.capture());
        verify(tracker).addListener(eq("session-2"), captor.capture());

        AgentExecutionTracker.AgentExecutionEventListener sessionOneDelegate = captor.getAllValues().get(0);
        AgentExecutionTracker.AgentExecutionEventListener sessionTwoDelegate = captor.getAllValues().get(1);
        assertNotSame(sessionOneDelegate, sessionTwoDelegate);

        adapter.removeListener("session-1", listener);
        verify(tracker).removeListener("session-1", sessionOneDelegate);
        verify(tracker, never()).removeListener("session-1", sessionTwoDelegate);

        adapter.removeListener("session-2", listener);
        verify(tracker).removeListener("session-2", sessionTwoDelegate);
    }

    @Test
    void shouldNotRemoveDifferentSessionDelegate() {
        AgentExecutionTracker tracker = mock(AgentExecutionTracker.class);
        WorkflowExecutionEventPortAdapter adapter = new WorkflowExecutionEventPortAdapter(tracker);
        WorkflowExecutionEventPort.Listener listener = mock(WorkflowExecutionEventPort.Listener.class);

        adapter.addListener("session-1", listener);
        adapter.addListener("session-2", listener);
        adapter.removeListener("session-1", listener);
        adapter.removeListener("session-1", listener);

        verify(tracker, times(1)).removeListener(eq("session-1"), org.mockito.ArgumentMatchers.any());
        verify(tracker, never()).removeListener(eq("session-2"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRegisterAgainAfterRemoval() {
        AgentExecutionTracker tracker = mock(AgentExecutionTracker.class);
        WorkflowExecutionEventPortAdapter adapter = new WorkflowExecutionEventPortAdapter(tracker);
        WorkflowExecutionEventPort.Listener listener = mock(WorkflowExecutionEventPort.Listener.class);

        adapter.addListener("session-1", listener);
        adapter.removeListener("session-1", listener);
        adapter.addListener("session-1", listener);

        ArgumentCaptor<AgentExecutionTracker.AgentExecutionEventListener> captor =
                ArgumentCaptor.forClass(AgentExecutionTracker.AgentExecutionEventListener.class);
        verify(tracker, times(2)).addListener(eq("session-1"), captor.capture());
        verify(tracker).removeListener(eq("session-1"), eq(captor.getAllValues().get(0)));
        assertNotSame(captor.getAllValues().get(0), captor.getAllValues().get(1));
        assertEquals(2, captor.getAllValues().size());
    }

    @Test
    void shouldRollbackListenerMappingWhenTrackerRegistrationFails() {
        AgentExecutionTracker tracker = mock(AgentExecutionTracker.class);
        WorkflowExecutionEventPortAdapter adapter = new WorkflowExecutionEventPortAdapter(tracker);
        WorkflowExecutionEventPort.Listener listener = mock(WorkflowExecutionEventPort.Listener.class);
        RuntimeException expected = new RuntimeException("boom");
        doThrow(expected)
                .doNothing()
                .when(tracker)
                .addListener(eq("session-1"), org.mockito.ArgumentMatchers.any());

        RuntimeException actual =
                assertThrows(RuntimeException.class, () -> adapter.addListener("session-1", listener));
        assertEquals(expected, actual);

        adapter.addListener("session-1", listener);

        verify(tracker, times(2)).addListener(eq("session-1"), org.mockito.ArgumentMatchers.any());
        verify(tracker, never()).removeListener(eq("session-1"), org.mockito.ArgumentMatchers.any());
    }
}
