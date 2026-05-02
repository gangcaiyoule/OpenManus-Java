package com.openmanus.sandbox.infra;

import com.openmanus.infra.sandbox.SandboxClient;
import com.openmanus.infra.sandbox.VncSandboxClient;
import com.openmanus.infra.sandbox.VncSandboxInfo;
import com.openmanus.sandbox.domain.model.SandboxCommandResult;
import com.openmanus.sandbox.domain.model.SessionSandboxInfo;
import com.openmanus.sandbox.domain.port.SandboxRuntimePort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DockerSessionSandboxRuntimeAdapter implements SandboxRuntimePort {

    private final SandboxClient sandboxClient;
    private final VncSandboxClient vncSandboxClient;
    private final Map<String, VncSandboxInfo> vncSandboxes = new ConcurrentHashMap<>();

    public DockerSessionSandboxRuntimeAdapter(SandboxClient sandboxClient, VncSandboxClient vncSandboxClient) {
        this.sandboxClient = sandboxClient;
        this.vncSandboxClient = vncSandboxClient;
    }

    @Override
    public SessionSandboxInfo createSandbox(String sessionId) {
        SandboxClient.SessionContainer container = sandboxClient.ensureSessionContainer(sessionId);
        VncSandboxInfo vncSandboxInfo = getOrCreateVncSandbox(sessionId);
        return SessionSandboxInfo.builder()
                .sessionId(sessionId)
                .containerId(container.containerId())
                .workspaceRoot(container.workspaceRoot())
                .vncUrl(vncSandboxInfo.getVncUrl())
                .createdAt(LocalDateTime.now())
                .status(SessionSandboxInfo.SandboxStatus.RUNNING)
                .build();
    }

    @Override
    public SessionSandboxInfo refreshSandboxInfo(String sessionId, SessionSandboxInfo sandboxInfo) {
        if (sandboxInfo == null) {
            return null;
        }
        boolean running = sandboxClient.isSessionRunning(sessionId);
        VncSandboxInfo vncSandboxInfo = vncSandboxes.get(sessionId);
        boolean vncRunning = vncSandboxInfo != null && vncSandboxClient.isContainerRunning(vncSandboxInfo.getContainerId());
        return SessionSandboxInfo.builder()
                .sessionId(sandboxInfo.getSessionId())
                .containerId(sandboxClient.getContainerId(sessionId))
                .workspaceRoot(sandboxInfo.getWorkspaceRoot())
                .vncUrl(vncRunning ? vncSandboxInfo.getVncUrl() : sandboxInfo.getVncUrl())
                .createdAt(sandboxInfo.getCreatedAt())
                .status(running || vncRunning ? SessionSandboxInfo.SandboxStatus.RUNNING : SessionSandboxInfo.SandboxStatus.STOPPED)
                .build();
    }

    @Override
    public SandboxCommandResult executeCommand(String sessionId, String command, String cwd, int timeoutSeconds) {
        var result = sandboxClient.executeCommand(sessionId, command, cwd, timeoutSeconds);
        return new SandboxCommandResult(result.stdout(), result.stderr(), result.exitCode());
    }

    @Override
    public SandboxCommandResult openBrowserUrl(String sessionId, String url) {
        VncSandboxInfo vncSandboxInfo = getOrCreateVncSandbox(sessionId);
        var result = vncSandboxClient.openBrowserUrl(vncSandboxInfo.getContainerId(), url);
        return new SandboxCommandResult(result.stdout(), result.stderr(), result.exitCode());
    }

    @Override
    public SandboxCommandResult executePython(String sessionId, String script, int timeoutSeconds) {
        var result = sandboxClient.executePython(sessionId, script, timeoutSeconds);
        return new SandboxCommandResult(result.stdout(), result.stderr(), result.exitCode());
    }

    @Override
    public String readTextFile(String sessionId, String path) {
        return sandboxClient.readTextFile(sessionId, path);
    }

    @Override
    public void writeTextFile(String sessionId, String path, String content) {
        sandboxClient.writeTextFile(sessionId, path, content);
    }

    @Override
    public void destroySandbox(String sessionId) {
        sandboxClient.destroySessionContainer(sessionId);
        VncSandboxInfo vncSandboxInfo = vncSandboxes.remove(sessionId);
        if (vncSandboxInfo != null) {
            vncSandboxClient.destroyVncSandbox(vncSandboxInfo.getContainerId());
        }
    }

    private VncSandboxInfo getOrCreateVncSandbox(String sessionId) {
        VncSandboxInfo existing = vncSandboxes.get(sessionId);
        if (existing != null && vncSandboxClient.isContainerRunning(existing.getContainerId())) {
            return existing;
        }
        synchronized (vncSandboxes) {
            VncSandboxInfo current = vncSandboxes.get(sessionId);
            if (current != null && vncSandboxClient.isContainerRunning(current.getContainerId())) {
                return current;
            }
            VncSandboxInfo created = vncSandboxClient.createVncSandbox(sessionId);
            vncSandboxes.put(sessionId, created);
            return created;
        }
    }
}
