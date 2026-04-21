package com.openmanus.domain.service;

import com.openmanus.domain.model.SessionSandboxInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionSandboxManagerLifecycleTest {

    @Test
    void shouldReuseAvailableSandboxWithoutCreatingAnotherOne() {
        SessionSandboxClient sessionSandboxClient = mock(SessionSandboxClient.class);
        SessionFileSandboxDirectoryProvider directoryProvider = mock(SessionFileSandboxDirectoryProvider.class);
        SessionSandboxManager manager = new SessionSandboxManager(sessionSandboxClient, directoryProvider);
        SessionSandboxInfo runningInfo = sandboxInfo("session-reuse", SessionSandboxInfo.SandboxStatus.RUNNING);

        putSandbox(manager, runningInfo);

        SessionSandboxInfo result = manager.getOrCreateSandbox("session-reuse");

        assertSame(runningInfo, result);
        verify(sessionSandboxClient, never()).createSandbox("session-reuse");
    }

    @Test
    void shouldCreateAndCacheSandboxUsingProvisionedSessionSnapshot() {
        SessionSandboxClient sessionSandboxClient = mock(SessionSandboxClient.class);
        SessionFileSandboxDirectoryProvider directoryProvider = mock(SessionFileSandboxDirectoryProvider.class);
        SessionSandboxManager manager = new SessionSandboxManager(sessionSandboxClient, directoryProvider);
        SessionSandboxInfo provisionedInfo = SessionSandboxInfo.builder()
                .sessionId("session-create")
                .vncUrl("http://localhost:6080")
                .build();

        when(sessionSandboxClient.createSandbox("session-create")).thenReturn(provisionedInfo);

        SessionSandboxInfo result = manager.getOrCreateSandbox("session-create");

        assertSame(provisionedInfo, result);
        assertEquals(SessionSandboxInfo.SandboxStatus.RUNNING, result.getStatus());
        assertNotNull(result.getCreatedAt());
        verify(sessionSandboxClient).createSandbox("session-create");
    }

    @Test
    void shouldMarkSandboxAsErrorWhenProvisionReturnsNull() {
        SessionSandboxClient sessionSandboxClient = mock(SessionSandboxClient.class);
        SessionFileSandboxDirectoryProvider directoryProvider = mock(SessionFileSandboxDirectoryProvider.class);
        SessionSandboxManager manager = new SessionSandboxManager(sessionSandboxClient, directoryProvider);

        when(sessionSandboxClient.createSandbox("session-null")).thenReturn(null);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> manager.getOrCreateSandbox("session-null"));

        assertEquals("创建会话沙箱失败: 会话沙箱创建返回空结果", exception.getMessage());
        Optional<SessionSandboxInfo> result = manager.getSandboxInfo("session-null");
        assertTrue(result.isPresent());
        assertEquals(SessionSandboxInfo.SandboxStatus.ERROR, result.get().getStatus());
        verify(sessionSandboxClient).createSandbox("session-null");
    }

    @Test
    void shouldMarkRunningSandboxAsStoppedWhenContainerIsNoLongerRunning() {
        SessionSandboxClient sessionSandboxClient = mock(SessionSandboxClient.class);
        SessionFileSandboxDirectoryProvider directoryProvider = mock(SessionFileSandboxDirectoryProvider.class);
        SessionSandboxManager manager = new SessionSandboxManager(sessionSandboxClient, directoryProvider);
        SessionSandboxInfo runningInfo = sandboxInfo("session-running", SessionSandboxInfo.SandboxStatus.RUNNING);

        putSandbox(manager, runningInfo);
        when(sessionSandboxClient.refreshSandboxInfo("session-running", runningInfo))
                .thenReturn(sandboxInfo("session-running", SessionSandboxInfo.SandboxStatus.STOPPED));

        Optional<SessionSandboxInfo> result = manager.getSandboxInfo("session-running");

        assertTrue(result.isPresent());
        assertEquals(SessionSandboxInfo.SandboxStatus.STOPPED, result.get().getStatus());
        verify(sessionSandboxClient).refreshSandboxInfo("session-running", runningInfo);
    }

    @Test
    void shouldKeepRunningSandboxStatusWhenContainerIsStillRunning() {
        SessionSandboxClient sessionSandboxClient = mock(SessionSandboxClient.class);
        SessionFileSandboxDirectoryProvider directoryProvider = mock(SessionFileSandboxDirectoryProvider.class);
        SessionSandboxManager manager = new SessionSandboxManager(sessionSandboxClient, directoryProvider);
        SessionSandboxInfo runningInfo = sandboxInfo("session-running-ok", SessionSandboxInfo.SandboxStatus.RUNNING);

        putSandbox(manager, runningInfo);
        when(sessionSandboxClient.refreshSandboxInfo("session-running-ok", runningInfo)).thenReturn(runningInfo);

        Optional<SessionSandboxInfo> result = manager.getSandboxInfo("session-running-ok");

        assertTrue(result.isPresent());
        assertSame(runningInfo, result.get());
        assertEquals(SessionSandboxInfo.SandboxStatus.RUNNING, result.get().getStatus());
        verify(sessionSandboxClient).refreshSandboxInfo("session-running-ok", runningInfo);
    }

    @Test
    void shouldKeepCreatingSandboxStatusWithoutContainerProbe() {
        SessionSandboxClient sessionSandboxClient = mock(SessionSandboxClient.class);
        SessionFileSandboxDirectoryProvider directoryProvider = mock(SessionFileSandboxDirectoryProvider.class);
        SessionSandboxManager manager = new SessionSandboxManager(sessionSandboxClient, directoryProvider);
        SessionSandboxInfo creatingInfo = sandboxInfo("session-creating", SessionSandboxInfo.SandboxStatus.CREATING);

        putSandbox(manager, creatingInfo);
        when(sessionSandboxClient.refreshSandboxInfo("session-creating", creatingInfo)).thenReturn(creatingInfo);

        Optional<SessionSandboxInfo> result = manager.getSandboxInfo("session-creating");

        assertTrue(result.isPresent());
        assertEquals(SessionSandboxInfo.SandboxStatus.CREATING, result.get().getStatus());
        verify(sessionSandboxClient).refreshSandboxInfo("session-creating", creatingInfo);
    }

    @Test
    void shouldKeepErrorSandboxStatusWithoutContainerProbe() {
        SessionSandboxClient sessionSandboxClient = mock(SessionSandboxClient.class);
        SessionFileSandboxDirectoryProvider directoryProvider = mock(SessionFileSandboxDirectoryProvider.class);
        SessionSandboxManager manager = new SessionSandboxManager(sessionSandboxClient, directoryProvider);
        SessionSandboxInfo errorInfo = sandboxInfo("session-error", SessionSandboxInfo.SandboxStatus.ERROR);

        putSandbox(manager, errorInfo);
        when(sessionSandboxClient.refreshSandboxInfo("session-error", errorInfo)).thenReturn(errorInfo);

        Optional<SessionSandboxInfo> result = manager.getSandboxInfo("session-error");

        assertTrue(result.isPresent());
        assertEquals(SessionSandboxInfo.SandboxStatus.ERROR, result.get().getStatus());
        verify(sessionSandboxClient).refreshSandboxInfo("session-error", errorInfo);
    }

    @Test
    void shouldKeepRunningStatusWhenRefreshClientReturnsCachedSnapshot() {
        SessionSandboxClient sessionSandboxClient = mock(SessionSandboxClient.class);
        SessionFileSandboxDirectoryProvider directoryProvider = mock(SessionFileSandboxDirectoryProvider.class);
        SessionSandboxManager manager = new SessionSandboxManager(sessionSandboxClient, directoryProvider);
        SessionSandboxInfo runningInfo = sandboxInfo("session-running-cached", SessionSandboxInfo.SandboxStatus.RUNNING);

        putSandbox(manager, runningInfo);
        when(sessionSandboxClient.refreshSandboxInfo("session-running-cached", runningInfo)).thenReturn(runningInfo);

        Optional<SessionSandboxInfo> result = manager.getSandboxInfo("session-running-cached");

        assertTrue(result.isPresent());
        assertEquals(SessionSandboxInfo.SandboxStatus.RUNNING, result.get().getStatus());
        verify(sessionSandboxClient).refreshSandboxInfo("session-running-cached", runningInfo);
    }

    @Test
    void shouldKeepCachedSnapshotWhenRefreshReturnsNull() {
        SessionSandboxClient sessionSandboxClient = mock(SessionSandboxClient.class);
        SessionFileSandboxDirectoryProvider directoryProvider = mock(SessionFileSandboxDirectoryProvider.class);
        SessionSandboxManager manager = new SessionSandboxManager(sessionSandboxClient, directoryProvider);
        SessionSandboxInfo runningInfo = sandboxInfo("session-refresh-null", SessionSandboxInfo.SandboxStatus.RUNNING);

        putSandbox(manager, runningInfo);
        when(sessionSandboxClient.refreshSandboxInfo("session-refresh-null", runningInfo)).thenReturn(null);

        Optional<SessionSandboxInfo> result = manager.getSandboxInfo("session-refresh-null");

        assertTrue(result.isPresent());
        assertSame(runningInfo, result.get());
        verify(sessionSandboxClient).refreshSandboxInfo("session-refresh-null", runningInfo);
    }

    @Test
    void shouldReturnEmptyWhenSandboxInfoDoesNotExist() {
        SessionSandboxClient sessionSandboxClient = mock(SessionSandboxClient.class);
        SessionFileSandboxDirectoryProvider directoryProvider = mock(SessionFileSandboxDirectoryProvider.class);
        SessionSandboxManager manager = new SessionSandboxManager(sessionSandboxClient, directoryProvider);

        Optional<SessionSandboxInfo> result = manager.getSandboxInfo("missing-session");

        assertFalse(result.isPresent());
        verify(sessionSandboxClient, never()).refreshSandboxInfo(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldKeepCachedStatusWhenProbeFailsForRunningSandbox() {
        SessionSandboxClient sessionSandboxClient = mock(SessionSandboxClient.class);
        SessionFileSandboxDirectoryProvider directoryProvider = mock(SessionFileSandboxDirectoryProvider.class);
        SessionSandboxManager manager = new SessionSandboxManager(sessionSandboxClient, directoryProvider);
        SessionSandboxInfo runningInfo = sandboxInfo("session-probe-failure", SessionSandboxInfo.SandboxStatus.RUNNING);

        putSandbox(manager, runningInfo);
        when(sessionSandboxClient.refreshSandboxInfo("session-probe-failure", runningInfo))
                .thenThrow(new IllegalStateException("sandbox inspect failed"));

        Optional<SessionSandboxInfo> result = manager.getSandboxInfo("session-probe-failure");

        assertTrue(result.isPresent());
        assertNotNull(result.get());
        assertSame(runningInfo, result.get());
        assertEquals(SessionSandboxInfo.SandboxStatus.RUNNING, runningInfo.getStatus());
        verify(sessionSandboxClient).refreshSandboxInfo("session-probe-failure", runningInfo);
    }

    @Test
    void shouldDestroyCachedSandboxThroughSessionLevelClient() {
        SessionSandboxClient sessionSandboxClient = mock(SessionSandboxClient.class);
        SessionFileSandboxDirectoryProvider directoryProvider = mock(SessionFileSandboxDirectoryProvider.class);
        SessionSandboxManager manager = new SessionSandboxManager(sessionSandboxClient, directoryProvider);
        SessionSandboxInfo runningInfo = sandboxInfo("session-destroy", SessionSandboxInfo.SandboxStatus.RUNNING);

        putSandbox(manager, runningInfo);
        manager.destroySandbox("session-destroy");

        verify(sessionSandboxClient).destroySandbox("session-destroy");
        assertFalse(manager.getSandboxInfo("session-destroy").isPresent());
    }

    @Test
    void shouldSwallowDestroyFailureAfterRemovingCachedSandbox() {
        SessionSandboxClient sessionSandboxClient = mock(SessionSandboxClient.class);
        SessionFileSandboxDirectoryProvider directoryProvider = mock(SessionFileSandboxDirectoryProvider.class);
        SessionSandboxManager manager = new SessionSandboxManager(sessionSandboxClient, directoryProvider);
        SessionSandboxInfo runningInfo = sandboxInfo("session-destroy-failure", SessionSandboxInfo.SandboxStatus.RUNNING);

        putSandbox(manager, runningInfo);
        org.mockito.Mockito.doThrow(new IllegalStateException("docker down"))
                .when(sessionSandboxClient).destroySandbox("session-destroy-failure");

        manager.destroySandbox("session-destroy-failure");

        verify(sessionSandboxClient).destroySandbox("session-destroy-failure");
        assertFalse(manager.getSandboxInfo("session-destroy-failure").isPresent());
    }

    @Test
    void shouldCleanupExpiredSandboxesOnly() {
        SessionSandboxClient sessionSandboxClient = mock(SessionSandboxClient.class);
        SessionFileSandboxDirectoryProvider directoryProvider = mock(SessionFileSandboxDirectoryProvider.class);
        SessionSandboxManager manager = new SessionSandboxManager(sessionSandboxClient, directoryProvider);

        putSandbox(manager, sandboxInfoAt("session-expired", SessionSandboxInfo.SandboxStatus.RUNNING,
                LocalDateTime.now().minusHours(3)));
        putSandbox(manager, sandboxInfoAt("session-active", SessionSandboxInfo.SandboxStatus.RUNNING,
                LocalDateTime.now().minusMinutes(30)));
        putSandbox(manager, SessionSandboxInfo.builder()
                .sessionId("session-no-time")
                .vncUrl("http://localhost:6080")
                .status(SessionSandboxInfo.SandboxStatus.RUNNING)
                .build());

        manager.cleanupExpiredSandboxes();

        verify(sessionSandboxClient).destroySandbox("session-expired");
        verify(sessionSandboxClient, never()).destroySandbox("session-active");
        verify(sessionSandboxClient, never()).destroySandbox("session-no-time");
        assertFalse(manager.getSandboxInfo("session-expired").isPresent());
        assertTrue(manager.getSandboxInfo("session-active").isPresent());
        assertTrue(manager.getSandboxInfo("session-no-time").isPresent());
    }

    @Test
    void shouldCleanupAllCachedSandboxesOnShutdown() {
        SessionSandboxClient sessionSandboxClient = mock(SessionSandboxClient.class);
        SessionFileSandboxDirectoryProvider directoryProvider = mock(SessionFileSandboxDirectoryProvider.class);
        SessionSandboxManager manager = new SessionSandboxManager(sessionSandboxClient, directoryProvider);

        putSandbox(manager, sandboxInfo("session-cleanup-1", SessionSandboxInfo.SandboxStatus.RUNNING));
        putSandbox(manager, sandboxInfo("session-cleanup-2", SessionSandboxInfo.SandboxStatus.ERROR));

        manager.cleanup();

        verify(sessionSandboxClient).destroySandbox("session-cleanup-1");
        verify(sessionSandboxClient).destroySandbox("session-cleanup-2");
        assertFalse(manager.getSandboxInfo("session-cleanup-1").isPresent());
        assertFalse(manager.getSandboxInfo("session-cleanup-2").isPresent());
    }

    private static SessionSandboxInfo sandboxInfo(
            String sessionId,
            SessionSandboxInfo.SandboxStatus status) {
        return sandboxInfoAt(sessionId, status, LocalDateTime.now());
    }

    private static SessionSandboxInfo sandboxInfoAt(
            String sessionId,
            SessionSandboxInfo.SandboxStatus status,
            LocalDateTime createdAt) {
        return SessionSandboxInfo.builder()
                .sessionId(sessionId)
                .vncUrl("http://localhost:6080")
                .status(status)
                .createdAt(createdAt)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static void putSandbox(SessionSandboxManager manager, SessionSandboxInfo info) {
        try {
            Field field = SessionSandboxManager.class.getDeclaredField("sessionSandboxMap");
            field.setAccessible(true);
            Map<String, SessionSandboxInfo> sessionSandboxMap =
                    (Map<String, SessionSandboxInfo>) field.get(manager);
            sessionSandboxMap.put(info.getSessionId(), info);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("无法注入会话沙箱映射", e);
        }
    }
}
