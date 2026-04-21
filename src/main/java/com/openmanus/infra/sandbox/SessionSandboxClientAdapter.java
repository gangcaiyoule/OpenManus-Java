package com.openmanus.infra.sandbox;

import com.openmanus.domain.model.SessionSandboxInfo;
import com.openmanus.domain.service.SessionSandboxClient;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionSandboxClientAdapter implements SessionSandboxClient {

    private final VncSandboxClient vncSandboxClient;
    private final Map<String, RuntimeSandboxHandle> runtimeSandboxHandles = new ConcurrentHashMap<>();

    public SessionSandboxClientAdapter(VncSandboxClient vncSandboxClient) {
        this.vncSandboxClient = vncSandboxClient;
    }

    @Override
    public SessionSandboxInfo createSandbox(String sessionId) {
        VncSandboxInfo sandboxInfo = vncSandboxClient.createVncSandbox(sessionId);
        runtimeSandboxHandles.put(sessionId,
                new RuntimeSandboxHandle(sandboxInfo.getContainerId(), sandboxInfo.getMappedPort()));
        return SessionSandboxInfo.builder()
                .sessionId(sessionId)
                .vncUrl(sandboxInfo.getVncUrl())
                .build();
    }

    @Override
    public SessionSandboxInfo refreshSandboxInfo(String sessionId, SessionSandboxInfo sandboxInfo) {
        RuntimeSandboxHandle runtimeHandle = runtimeSandboxHandles.get(sessionId);
        if (!shouldProbeRuntime(sandboxInfo, runtimeHandle)) {
            return sandboxInfo;
        }
        try {
            if (vncSandboxClient.isContainerRunning(runtimeHandle.containerId())) {
                return sandboxInfo;
            }
            return SessionSandboxInfo.builder()
                    .sessionId(sandboxInfo.getSessionId())
                    .vncUrl(sandboxInfo.getVncUrl())
                    .createdAt(sandboxInfo.getCreatedAt())
                    .status(SessionSandboxInfo.SandboxStatus.STOPPED)
                    .build();
        } catch (RuntimeException e) {
            return sandboxInfo;
        }
    }

    @Override
    public void destroySandbox(String sessionId) {
        RuntimeSandboxHandle runtimeHandle = runtimeSandboxHandles.remove(sessionId);
        if (runtimeHandle == null || runtimeHandle.containerId() == null || runtimeHandle.containerId().isBlank()) {
            return;
        }
        vncSandboxClient.destroyVncSandbox(runtimeHandle.containerId());
    }

    private boolean shouldProbeRuntime(SessionSandboxInfo sandboxInfo, RuntimeSandboxHandle runtimeHandle) {
        return sandboxInfo != null
                && sandboxInfo.getStatus() == SessionSandboxInfo.SandboxStatus.RUNNING
                && runtimeHandle != null
                && runtimeHandle.containerId() != null
                && !runtimeHandle.containerId().isBlank();
    }

    private record RuntimeSandboxHandle(String containerId, Integer mappedPort) {
    }
}
