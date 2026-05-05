package com.openmanus.infra.sandbox;

import com.openmanus.aiframework.runtime.AiCodeExecutionResult;
import com.openmanus.aiframework.runtime.AiCodeSandbox;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Infra adapter that exposes SandboxClient via runtime sandbox port.
 */
@Component
public class RuntimeCodeSandboxAdapter implements AiCodeSandbox {

    private static final String DEFAULT_USER_ID = "001";

    private final SandboxClient sandboxClient;

    public RuntimeCodeSandboxAdapter(SandboxClient sandboxClient) {
        this.sandboxClient = sandboxClient;
    }

    @Override
    public AiCodeExecutionResult executePython(String script, int timeoutSeconds) {
        ExecutionResult result = sandboxClient.executePython(currentSandboxKey(), script, timeoutSeconds);
        return new AiCodeExecutionResult(
                result.getStdout(),
                result.getStderr(),
                result.getExitCode()
        );
    }

    private static String currentSandboxKey() {
        String userId = MDC.get("userId");
        return userId == null || userId.isBlank() ? DEFAULT_USER_ID : userId;
    }
}
