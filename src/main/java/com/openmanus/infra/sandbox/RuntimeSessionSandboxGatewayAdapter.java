package com.openmanus.infra.sandbox;

import com.openmanus.aiframework.runtime.AiSessionSandboxGateway;
import com.openmanus.aiframework.runtime.AiSessionSandboxInfo;
import com.openmanus.domain.model.SessionSandboxInfo;
import com.openmanus.domain.service.SessionSandboxManager;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Optional;

@Component
public class RuntimeSessionSandboxGatewayAdapter implements AiSessionSandboxGateway {

    private final SessionSandboxManager sessionSandboxManager;

    public RuntimeSessionSandboxGatewayAdapter(SessionSandboxManager sessionSandboxManager) {
        this.sessionSandboxManager = sessionSandboxManager;
    }

    @Override
    public Optional<AiSessionSandboxInfo> getSandboxInfo(String sessionId) {
        return sessionSandboxManager.getSandboxInfo(sessionId).map(this::toRuntimeInfo);
    }

    @Override
    public AiSessionSandboxInfo getOrCreateSandbox(String sessionId) {
        return toRuntimeInfo(sessionSandboxManager.getOrCreateSandbox(sessionId));
    }

    @Override
    public Path getOrCreateFileSandboxRoot(String sessionId) {
        return sessionSandboxManager.getOrCreateFileSandboxRoot(sessionId);
    }

    private AiSessionSandboxInfo toRuntimeInfo(SessionSandboxInfo info) {
        return new AiSessionSandboxInfo(
                info.getSessionId(),
                null,
                info.getVncUrl(),
                null,
                info.getStatus() == null ? null : info.getStatus().name()
        );
    }
}
