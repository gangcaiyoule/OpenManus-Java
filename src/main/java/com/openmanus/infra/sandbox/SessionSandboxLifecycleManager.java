package com.openmanus.infra.sandbox;

import com.openmanus.domain.service.SessionSandboxManager;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SessionSandboxLifecycleManager {

    private final SessionSandboxManager sessionSandboxManager;

    public SessionSandboxLifecycleManager(SessionSandboxManager sessionSandboxManager) {
        this.sessionSandboxManager = sessionSandboxManager;
    }

    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void cleanupExpiredSandboxes() {
        sessionSandboxManager.cleanupExpiredSandboxes();
    }

    @PreDestroy
    public void cleanup() {
        sessionSandboxManager.cleanup();
    }
}
