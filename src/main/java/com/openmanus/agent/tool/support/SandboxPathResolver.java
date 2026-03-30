package com.openmanus.agent.tool.support;

import com.openmanus.domain.service.SessionSandboxManager;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class SandboxPathResolver {

    private final SessionSandboxManager sessionSandboxManager;

    public SandboxPathResolver(SessionSandboxManager sessionSandboxManager) {
        this.sessionSandboxManager = sessionSandboxManager;
    }

    public Path resolveSandboxPath(String userPath) {
        String sessionId = requireSessionId();
        Path sandboxRoot = sessionSandboxManager.getOrCreateFileSandboxRoot(sessionId).toAbsolutePath().normalize();

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
