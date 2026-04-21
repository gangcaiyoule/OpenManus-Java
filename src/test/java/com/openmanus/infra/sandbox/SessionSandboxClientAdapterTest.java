package com.openmanus.infra.sandbox;

import com.openmanus.domain.model.SessionSandboxInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionSandboxClientAdapterTest {

    @Test
    void shouldMapSandboxCreationToDomainModel() {
        VncSandboxClient vncSandboxClient = mock(VncSandboxClient.class);
        when(vncSandboxClient.createVncSandbox("session-1"))
                .thenReturn(new VncSandboxInfo("container-1", "http://vnc", 6080));

        SessionSandboxClientAdapter adapter = new SessionSandboxClientAdapter(vncSandboxClient);
        SessionSandboxInfo sandboxInfo = adapter.createSandbox("session-1");

        assertEquals("session-1", sandboxInfo.getSessionId());
        assertEquals("http://vnc", sandboxInfo.getVncUrl());
    }

    @Test
    void shouldKeepRunningSnapshotWhenContainerStillRunning() {
        VncSandboxClient vncSandboxClient = mock(VncSandboxClient.class);
        when(vncSandboxClient.createVncSandbox("session-1"))
                .thenReturn(new VncSandboxInfo("container-1", "http://vnc", 6080));
        when(vncSandboxClient.isContainerRunning("container-1")).thenReturn(true);
        SessionSandboxClientAdapter adapter = new SessionSandboxClientAdapter(vncSandboxClient);
        adapter.createSandbox("session-1");
        SessionSandboxInfo running = runningSandbox();

        SessionSandboxInfo refreshed = adapter.refreshSandboxInfo("session-1", running);

        assertSame(running, refreshed);
        verify(vncSandboxClient).isContainerRunning("container-1");
    }

    @Test
    void shouldReturnStoppedSnapshotWhenRunningContainerIsGone() {
        VncSandboxClient vncSandboxClient = mock(VncSandboxClient.class);
        when(vncSandboxClient.createVncSandbox("session-1"))
                .thenReturn(new VncSandboxInfo("container-1", "http://vnc", 6080));
        when(vncSandboxClient.isContainerRunning("container-1")).thenReturn(false);
        SessionSandboxClientAdapter adapter = new SessionSandboxClientAdapter(vncSandboxClient);
        adapter.createSandbox("session-1");

        SessionSandboxInfo refreshed = adapter.refreshSandboxInfo("session-1", runningSandbox());

        assertEquals(SessionSandboxInfo.SandboxStatus.STOPPED, refreshed.getStatus());
        assertEquals("http://vnc", refreshed.getVncUrl());
    }

    @Test
    void shouldSkipRuntimeProbeForNonRunningSandbox() {
        VncSandboxClient vncSandboxClient = mock(VncSandboxClient.class);
        SessionSandboxClientAdapter adapter = new SessionSandboxClientAdapter(vncSandboxClient);
        SessionSandboxInfo creating = SessionSandboxInfo.builder()
                .sessionId("session-1")
                .status(SessionSandboxInfo.SandboxStatus.CREATING)
                .build();

        assertSame(creating, adapter.refreshSandboxInfo("session-1", creating));
        verify(vncSandboxClient, never()).isContainerRunning(anyString());
    }

    @Test
    void shouldKeepCachedSnapshotWhenRuntimeProbeFails() {
        VncSandboxClient vncSandboxClient = mock(VncSandboxClient.class);
        when(vncSandboxClient.createVncSandbox("session-1"))
                .thenReturn(new VncSandboxInfo("container-1", "http://vnc", 6080));
        when(vncSandboxClient.isContainerRunning("container-1"))
                .thenThrow(new IllegalStateException("docker probe failed"));
        SessionSandboxClientAdapter adapter = new SessionSandboxClientAdapter(vncSandboxClient);
        adapter.createSandbox("session-1");
        SessionSandboxInfo running = runningSandbox();

        assertSame(running, adapter.refreshSandboxInfo("session-1", running));
    }

    @Test
    void shouldIgnoreDestroyWhenContainerIdMissing() {
        VncSandboxClient vncSandboxClient = mock(VncSandboxClient.class);
        SessionSandboxClientAdapter adapter = new SessionSandboxClientAdapter(vncSandboxClient);

        adapter.destroySandbox("session-1");

        verify(vncSandboxClient, never()).destroyVncSandbox(anyString());
    }

    @Test
    void shouldDestroySandboxByContainerIdWhenPresent() {
        VncSandboxClient vncSandboxClient = mock(VncSandboxClient.class);
        when(vncSandboxClient.createVncSandbox("session-1"))
                .thenReturn(new VncSandboxInfo("container-1", "http://vnc", 6080));
        SessionSandboxClientAdapter adapter = new SessionSandboxClientAdapter(vncSandboxClient);

        adapter.createSandbox("session-1");
        adapter.destroySandbox("session-1");

        verify(vncSandboxClient).destroyVncSandbox("container-1");
    }

    private SessionSandboxInfo runningSandbox() {
        return SessionSandboxInfo.builder()
                .sessionId("session-1")
                .vncUrl("http://vnc")
                .status(SessionSandboxInfo.SandboxStatus.RUNNING)
                .build();
    }
}
