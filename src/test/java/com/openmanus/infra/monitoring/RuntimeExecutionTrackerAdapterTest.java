package com.openmanus.infra.monitoring;

import com.openmanus.aiframework.runtime.AiExecutionEvent;
import com.openmanus.aiframework.runtime.AiExecutionStatus;
import com.openmanus.aiframework.runtime.AiExecutionTracker;
import com.openmanus.domain.model.AgentExecutionEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RuntimeExecutionTrackerAdapterTest {

    @Test
    void shouldDelegateLifecycleApis() {
        AgentExecutionTracker delegate = mock(AgentExecutionTracker.class);
        RuntimeExecutionTrackerAdapter adapter = new RuntimeExecutionTrackerAdapter(delegate);

        adapter.startAgentExecution("s1", "agent", "type", "input");
        adapter.endAgentExecution("s1", "agent", "type", "output", AiExecutionStatus.SUCCESS);
        adapter.endAgentExecution("s2", "agent", "type", "output", null);
        adapter.recordAgentError("s1", "agent", "type", "boom");

        verify(delegate).startAgentExecution("s1", "agent", "type", "input");
        verify(delegate).endAgentExecution("s1", "agent", "type", "output", AgentExecutionEvent.ExecutionStatus.SUCCESS);
        verify(delegate).endAgentExecution("s2", "agent", "type", "output", AgentExecutionEvent.ExecutionStatus.ERROR);
        verify(delegate).recordAgentError("s1", "agent", "type", "boom");
    }

    @Test
    void shouldBridgeListenerAndSupportRemove() {
        AgentExecutionTracker delegate = mock(AgentExecutionTracker.class);
        RuntimeExecutionTrackerAdapter adapter = new RuntimeExecutionTrackerAdapter(delegate);
        AiExecutionTracker.AgentExecutionEventListener listener = event -> { };

        adapter.addListener("s1", listener);

        ArgumentCaptor<AgentExecutionTracker.AgentExecutionEventListener> captor =
                ArgumentCaptor.forClass(AgentExecutionTracker.AgentExecutionEventListener.class);
        verify(delegate).addListener(eq("s1"), captor.capture());

        AgentExecutionEvent event = AgentExecutionEvent.builder()
                .sessionId("s1")
                .eventType(AgentExecutionEvent.EventType.AGENT_END)
                .status(AgentExecutionEvent.ExecutionStatus.SUCCESS)
                .build();
        AiExecutionEvent[] seen = new AiExecutionEvent[1];
        AiExecutionTracker.AgentExecutionEventListener observing = seenEvent -> seen[0] = seenEvent;
        adapter.addListener("s2", observing);
        verify(delegate).addListener(eq("s2"), any());
        ArgumentCaptor<AgentExecutionTracker.AgentExecutionEventListener> bridgeCaptor =
                ArgumentCaptor.forClass(AgentExecutionTracker.AgentExecutionEventListener.class);
        verify(delegate).addListener(eq("s2"), bridgeCaptor.capture());
        bridgeCaptor.getValue().onEvent(event);
        assertEquals("s1", seen[0].sessionId());
        assertEquals("AGENT_END", seen[0].eventType());
        assertEquals(AiExecutionStatus.SUCCESS, seen[0].status());

        bridgeCaptor.getValue().onEvent(null);
        assertNull(seen[0]);

        adapter.removeListener("s1", listener);
        verify(delegate).removeListener(eq("s1"), eq(captor.getValue()));
    }

    @Test
    void shouldKeepListenerRegistrationScopedBySession() {
        AgentExecutionTracker delegate = mock(AgentExecutionTracker.class);
        RuntimeExecutionTrackerAdapter adapter = new RuntimeExecutionTrackerAdapter(delegate);
        AiExecutionTracker.AgentExecutionEventListener listener = event -> { };

        adapter.addListener("s1", listener);
        adapter.addListener("s2", listener);

        ArgumentCaptor<AgentExecutionTracker.AgentExecutionEventListener> captor =
                ArgumentCaptor.forClass(AgentExecutionTracker.AgentExecutionEventListener.class);
        verify(delegate).addListener(eq("s1"), captor.capture());
        AgentExecutionTracker.AgentExecutionEventListener sessionOneBridge = captor.getValue();
        verify(delegate).addListener(eq("s2"), captor.capture());
        AgentExecutionTracker.AgentExecutionEventListener sessionTwoBridge = captor.getValue();
        assertEquals(2, captor.getAllValues().size());

        adapter.removeListener("s1", listener);

        verify(delegate).removeListener("s1", sessionOneBridge);
        verify(delegate, never()).removeListener("s2", sessionOneBridge);

        adapter.removeListener("s2", listener);

        verify(delegate).removeListener("s2", sessionTwoBridge);
    }

    @Test
    void shouldAvoidDuplicateDelegateRegistrationWithinSameSessionAndAllowReRegisterAfterRemove() {
        AgentExecutionTracker delegate = mock(AgentExecutionTracker.class);
        RuntimeExecutionTrackerAdapter adapter = new RuntimeExecutionTrackerAdapter(delegate);
        AiExecutionTracker.AgentExecutionEventListener listener = event -> { };

        adapter.addListener("s1", listener);
        adapter.addListener("s1", listener);

        ArgumentCaptor<AgentExecutionTracker.AgentExecutionEventListener> captor =
                ArgumentCaptor.forClass(AgentExecutionTracker.AgentExecutionEventListener.class);
        verify(delegate, times(1)).addListener(eq("s1"), captor.capture());
        AgentExecutionTracker.AgentExecutionEventListener firstBridge = captor.getValue();

        adapter.removeListener("s1", listener);
        verify(delegate).removeListener("s1", firstBridge);

        clearInvocations(delegate);

        adapter.addListener("s1", listener);

        verify(delegate, times(1)).addListener(eq("s1"), captor.capture());
    }

    @Test
    void shouldIgnoreRemoveWhenListenerNeverAdded() {
        AgentExecutionTracker delegate = mock(AgentExecutionTracker.class);
        RuntimeExecutionTrackerAdapter adapter = new RuntimeExecutionTrackerAdapter(delegate);

        adapter.removeListener("s1", event -> { });

        verify(delegate, never()).removeListener(eq("s1"), any());
    }
}
