package com.openmanus.infra.sandbox;

import com.openmanus.aiframework.runtime.AiVncSandboxClient;
import com.openmanus.aiframework.runtime.AiVncSandboxInfo;
import org.springframework.stereotype.Component;

/**
 * Infra adapter that exposes VNC sandbox client via runtime port.
 */
@Component
public class RuntimeVncSandboxClientAdapter implements AiVncSandboxClient {

    private final VncSandboxClient delegate;

    public RuntimeVncSandboxClientAdapter(VncSandboxClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public AiVncSandboxInfo createVncSandbox(String sessionId) {
        VncSandboxInfo info = delegate.createVncSandbox(sessionId);
        return new AiVncSandboxInfo(info.getContainerId(), info.getVncUrl(), info.getMappedPort());
    }

    @Override
    public void destroyVncSandbox(String containerId) {
        delegate.destroyVncSandbox(containerId);
    }

    @Override
    public boolean isContainerRunning(String containerId) {
        return delegate.isContainerRunning(containerId);
    }
}
