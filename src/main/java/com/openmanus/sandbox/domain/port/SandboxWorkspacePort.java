package com.openmanus.sandbox.domain.port;

/**
 * 会话级容器工作区路径 port。
 */
public interface SandboxWorkspacePort {

    String getWorkspaceRoot(String sessionId);

    String resolveWorkspacePath(String sessionId, String userPath);
}
