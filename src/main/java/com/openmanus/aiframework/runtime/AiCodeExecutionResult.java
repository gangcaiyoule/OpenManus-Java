package com.openmanus.aiframework.runtime;

/**
 * Runtime-level DTO for code execution result.
 */
public record AiCodeExecutionResult(
        String stdout,
        String stderr,
        int exitCode
) {
}
