package com.openmanus.infra.monitoring;

import com.openmanus.domain.model.AgentExecutionEvent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentExecutionTrackerSessionListenerTest {

    @Test
    void shouldNotifyOnlyMatchedSessionListenersAndStopAfterRemoval() {
        AgentExecutionTracker tracker = new AgentExecutionTracker();
        AtomicInteger sessionAEvents = new AtomicInteger(0);
        AtomicInteger sessionBEvents = new AtomicInteger(0);

        AgentExecutionTracker.AgentExecutionEventListener sessionAListener = event -> sessionAEvents.incrementAndGet();
        AgentExecutionTracker.AgentExecutionEventListener sessionBListener = event -> sessionBEvents.incrementAndGet();

        tracker.addListener("session-a", sessionAListener);
        tracker.addListener("session-b", sessionBListener);

        tracker.recordCustomEvent(AgentExecutionEvent.builder().sessionId("session-a").build());
        tracker.recordCustomEvent(AgentExecutionEvent.builder().sessionId("session-b").build());
        tracker.recordCustomEvent(AgentExecutionEvent.builder().sessionId("session-a").build());

        assertEquals(2, sessionAEvents.get());
        assertEquals(1, sessionBEvents.get());

        tracker.removeListener("session-a", sessionAListener);
        tracker.recordCustomEvent(AgentExecutionEvent.builder().sessionId("session-a").build());

        assertEquals(2, sessionAEvents.get());
        assertEquals(1, sessionBEvents.get());
    }

    @Test
    void shouldRejectReservedLegacyGlobalBucketSessionId() {
        AgentExecutionTracker tracker = new AgentExecutionTracker();
        AgentExecutionTracker.AgentExecutionEventListener listener = event -> {};

        assertThrows(IllegalArgumentException.class, () -> tracker.addListener("__all__", listener));
        assertThrows(IllegalArgumentException.class, () -> tracker.removeListener("__all__", listener));
    }

    @Test
    void shouldRejectBlankSessionIdForSessionScopedListenerApis() {
        AgentExecutionTracker tracker = new AgentExecutionTracker();
        AgentExecutionTracker.AgentExecutionEventListener listener = event -> {};

        assertThrows(IllegalArgumentException.class, () -> tracker.addListener(null, listener));
        assertThrows(IllegalArgumentException.class, () -> tracker.addListener("", listener));
        assertThrows(IllegalArgumentException.class, () -> tracker.addListener("   ", listener));

        assertThrows(IllegalArgumentException.class, () -> tracker.removeListener(null, listener));
        assertThrows(IllegalArgumentException.class, () -> tracker.removeListener("", listener));
        assertThrows(IllegalArgumentException.class, () -> tracker.removeListener("   ", listener));
    }
}
