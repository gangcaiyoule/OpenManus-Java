package com.openmanus.aiframework.runtime;

/**
 * Runtime abstraction for code execution sandbox.
 */
public interface AiCodeSandbox {

    /**
     * Execute Python code in sandbox runtime.
     */
    AiCodeExecutionResult executePython(String script, int timeoutSeconds);
}
