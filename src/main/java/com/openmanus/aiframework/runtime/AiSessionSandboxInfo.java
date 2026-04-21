package com.openmanus.aiframework.runtime;

/**
 * 会话沙箱信息（runtime 中立类型）。
 */
public record AiSessionSandboxInfo(
        String sessionId,
        String containerId,
        String vncUrl,
        Integer mappedPort,
        String status
) {

    public boolean isAvailable() {
        return "RUNNING".equals(status) && vncUrl != null && !vncUrl.isEmpty();
    }
}
