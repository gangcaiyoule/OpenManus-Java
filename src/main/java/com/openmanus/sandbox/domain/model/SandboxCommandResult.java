package com.openmanus.sandbox.domain.model;

/**
 * Session sandbox command execution result.
 */
public record SandboxCommandResult(String stdout, String stderr, int exitCode) {

    public SandboxCommandResult {
        stdout = stdout == null ? "" : stdout;
        stderr = stderr == null ? "" : stderr;
    }
}
