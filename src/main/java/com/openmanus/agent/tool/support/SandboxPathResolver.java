package com.openmanus.agent.tool.support;

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
        Path sandboxRoot = sessionSandboxGateway.getOrCreateFileSandboxRoot(sessionId).toAbsolutePath().normalize();

        String candidate = userPath == null || userPath.isBlank() ? "." : userPath;
        Path relativeCandidate = Paths.get(candidate);
        Path resolved = relativeCandidate.isAbsolute()
                ? sandboxRoot.resolve(relativeCandidate.toString().replaceFirst("^[/\\\\]+", ""))
                : sandboxRoot.resolve(relativeCandidate);
        resolved = resolved.toAbsolutePath().normalize();

        if (!resolved.startsWith(sandboxRoot)) {
            throw new SecurityException("禁止访问沙盒外路径: " + userPath);
        }
        return resolved;
    }

    private String requireSessionId() {
        String sessionId = MDC.get("sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            throw new SecurityException("缺少会话ID，拒绝访问文件沙盒");
        }
        return sessionId;
    }
}
