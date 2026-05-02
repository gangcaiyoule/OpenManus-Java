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

    private final SandboxClient sandboxClient;

    public RuntimeCodeSandboxAdapter(SandboxClient sandboxClient) {
        this.sandboxClient = sandboxClient;
    }

    @Override
    public AiCodeExecutionResult executePython(String script, int timeoutSeconds) {
        String sessionId = MDC.get("sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            throw new SecurityException("缺少会话ID，拒绝执行 Python 代码");
        }
        ExecutionResult result = sandboxClient.executePython(sessionId, script, timeoutSeconds);
        return new AiCodeExecutionResult(
                result.getStdout(),
                result.getStderr(),
                result.getExitCode()
        );
    }
}
