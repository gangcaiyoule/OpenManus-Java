package com.openmanus.aiframework.runtime;

import java.nio.file.Path;
import java.util.Optional;

/**
 * 会话沙箱访问端口（供 agent 层使用）。
 */
public interface AiSessionSandboxGateway {

    Optional<AiSessionSandboxInfo> getSandboxInfo(String sessionId);

    AiSessionSandboxInfo getOrCreateSandbox(String sessionId);

    Path getOrCreateFileSandboxRoot(String sessionId);
}
