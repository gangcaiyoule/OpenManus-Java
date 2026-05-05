package com.openmanus.sandbox.support;

import com.openmanus.aiframework.runtime.AiSessionSandboxGateway;
import org.slf4j.MDC;

import java.nio.file.Path;
import java.nio.file.Paths;

public class SandboxPathResolver {

    private static final String DEFAULT_USER_ID = "001";

    private final AiSessionSandboxGateway sessionSandboxGateway;

    public SandboxPathResolver(AiSessionSandboxGateway sessionSandboxGateway) {
        this.sessionSandboxGateway = sessionSandboxGateway;
    }

    public Path resolveSandboxPath(String userPath) {
        String sandboxKey = currentSandboxKey();
        return Paths.get(sessionSandboxGateway.resolveWorkspacePath(sandboxKey, userPath)).normalize();
    }

    public String readTextFile(String userPath) {
        String sandboxKey = currentSandboxKey();
        String resolved = sessionSandboxGateway.resolveWorkspacePath(sandboxKey, userPath);
        return sessionSandboxGateway.readTextFile(sandboxKey, resolved);
    }

    public String currentSandboxKey() {
        String userId = MDC.get("userId");
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        return DEFAULT_USER_ID;
    }
}
