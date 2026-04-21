package com.openmanus.aiframework.runtime;

/**
 * Runtime abstraction for session-scoped VNC sandbox management.
 */
public interface AiVncSandboxClient {

    AiVncSandboxInfo createVncSandbox(String sessionId);

    void destroyVncSandbox(String containerId);

    boolean isContainerRunning(String containerId);
}
