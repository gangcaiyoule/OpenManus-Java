package com.openmanus.sandbox.support;

import com.openmanus.aiframework.runtime.AiSessionSandboxGateway;
import org.slf4j.MDC;

import java.nio.file.Path;
import java.nio.file.Paths;

public class SandboxPathResolver {

    private final AiSessionSandboxGateway sessionSandboxGateway;

    public SandboxPathResolver(AiSessionSandboxGateway sessionSandboxGateway) {
        this.sessionSandboxGateway = sessionSandboxGateway;
    }

    public Path resolveSandboxPath(String userPath) {
        String sessionId = requireSessionId();
        return Paths.get(sessionSandboxGateway.resolveWorkspacePath(sessionId, userPath)).normalize();
    }

    public String readTextFile(String userPath) {
        String sessionId = requireSessionId();
        String resolved = sessionSandboxGateway.resolveWorkspacePath(sessionId, userPath);
        return sessionSandboxGateway.readTextFile(sessionId, resolved);
    }

    private String requireSessionId() {
        String sessionId = MDC.get("sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            throw new SecurityException("缺少会话ID，拒绝访问文件沙盒");
        }
        return sessionId;
    }
}
