package com.openmanus.aiframework.runtime;

/**
 * Runtime-level VNC sandbox metadata.
 */
public record AiVncSandboxInfo(
        String containerId,
        String vncUrl,
        int mappedPort
) {
}
