package com.openmanus.sandbox.infra;

import com.openmanus.sandbox.application.SandboxSessionApplicationService;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SandboxLifecycleManager {

    private final SandboxSessionApplicationService sandboxSessionApplicationService;

    public SandboxLifecycleManager(SandboxSessionApplicationService sandboxSessionApplicationService) {
        this.sandboxSessionApplicationService = sandboxSessionApplicationService;
    }

    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void cleanupExpiredSandboxes() {
        sandboxSessionApplicationService.cleanupExpiredSandboxes();
    }

    @PreDestroy
    public void cleanup() {
        sandboxSessionApplicationService.cleanup();
    }
}
