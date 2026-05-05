package com.openmanus.aiframework.runtime;

import java.util.Optional;

/**
 * 会话沙箱访问端口（供 agent 层使用）。
 */
public interface AiSessionSandboxGateway {

    Optional<AiSessionSandboxInfo> getSandboxInfo(String sessionId);

    AiSessionSandboxInfo getOrCreateSandbox(String sessionId);

    String getWorkspaceRoot(String sessionId);

    String resolveWorkspacePath(String sessionId, String userPath);

    AiSandboxCommandResult executeCommand(String sessionId, String command, String cwd, int timeoutSeconds);

    AiSandboxCommandResult openBrowserUrl(String sessionId, String url);

    String readTextFile(String sessionId, String path);

    void writeTextFile(String sessionId, String path, String content);
}
