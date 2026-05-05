package com.openmanus.aiframework.runtime;

/**
 * Runtime-neutral command execution result for session sandboxes.
 */
public record AiSandboxCommandResult(String stdout, String stderr, int exitCode) {

    public AiSandboxCommandResult {
        stdout = stdout == null ? "" : stdout;
        stderr = stderr == null ? "" : stderr;
    }
}
