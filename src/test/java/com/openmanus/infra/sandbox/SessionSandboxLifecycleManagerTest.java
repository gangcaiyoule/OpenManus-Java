package com.openmanus.infra.sandbox;

import com.openmanus.domain.service.SessionSandboxManager;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SessionSandboxLifecycleManagerTest {

    @Test
    void shouldDelegateScheduledCleanupToSessionSandboxManager() {
        SessionSandboxManager sessionSandboxManager = mock(SessionSandboxManager.class);
        SessionSandboxLifecycleManager lifecycleManager =
                new SessionSandboxLifecycleManager(sessionSandboxManager);

        lifecycleManager.cleanupExpiredSandboxes();

        verify(sessionSandboxManager).cleanupExpiredSandboxes();
    }

    @Test
    void shouldDelegateShutdownCleanupToSessionSandboxManager() {
        SessionSandboxManager sessionSandboxManager = mock(SessionSandboxManager.class);
        SessionSandboxLifecycleManager lifecycleManager =
                new SessionSandboxLifecycleManager(sessionSandboxManager);

        lifecycleManager.cleanup();

        verify(sessionSandboxManager).cleanup();
    }
}
