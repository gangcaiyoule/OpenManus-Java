package com.openmanus.sandbox.infra;

import com.openmanus.infra.sandbox.VncSandboxClient;
import com.openmanus.infra.sandbox.VncSandboxInfo;
import com.openmanus.sandbox.domain.model.SessionSandboxInfo;
import com.openmanus.sandbox.domain.port.SandboxRuntimePort;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VncSandboxRuntimeAdapter implements SandboxRuntimePort {

    private final VncSandboxClient vncSandboxClient;
    private final Map<String, RuntimeSandboxHandle> runtimeSandboxHandles = new ConcurrentHashMap<>();

    public VncSandboxRuntimeAdapter(VncSandboxClient vncSandboxClient) {
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
    public com.openmanus.sandbox.domain.model.SandboxCommandResult executeCommand(String sessionId, String command, String cwd, int timeoutSeconds) {
        throw new UnsupportedOperationException("VNC sandbox does not support command execution");
    }

    @Override
    public com.openmanus.sandbox.domain.model.SandboxCommandResult openBrowserUrl(String sessionId, String url) {
        getOrCreateSandboxInfo(sessionId);
        RuntimeSandboxHandle runtimeHandle = runtimeSandboxHandles.get(sessionId);
        if (runtimeHandle == null) {
            return new com.openmanus.sandbox.domain.model.SandboxCommandResult("", "VNC sandbox is not ready", 1);
        }
        var result = vncSandboxClient.openBrowserUrl(runtimeHandle.containerId(), url);
        return new com.openmanus.sandbox.domain.model.SandboxCommandResult(result.stdout(), result.stderr(), result.exitCode());
    }

    @Override
    public com.openmanus.sandbox.domain.model.SandboxCommandResult executePython(String sessionId, String script, int timeoutSeconds) {
        throw new UnsupportedOperationException("VNC sandbox does not support python execution");
    }

    @Override
    public String readTextFile(String sessionId, String path) {
        throw new UnsupportedOperationException("VNC sandbox does not support file reads");
    }

    @Override
    public void writeTextFile(String sessionId, String path, String content) {
        throw new UnsupportedOperationException("VNC sandbox does not support file writes");
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

    private SessionSandboxInfo getOrCreateSandboxInfo(String sessionId) {
        RuntimeSandboxHandle runtimeHandle = runtimeSandboxHandles.get(sessionId);
        if (runtimeHandle != null && vncSandboxClient.isContainerRunning(runtimeHandle.containerId())) {
            return SessionSandboxInfo.builder()
                    .sessionId(sessionId)
                    .status(SessionSandboxInfo.SandboxStatus.RUNNING)
                    .build();
        }
        return createSandbox(sessionId);
    }

    private record RuntimeSandboxHandle(String containerId, Integer mappedPort) {
    }
}
