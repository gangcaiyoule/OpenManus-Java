package com.openmanus.aiframework.runtime;

/**
 * Runtime-level metadata pointer for one persisted tool-result artifact.
 */
public record ToolResultArtifactRef(
        String artifactId,
        String toolName,
        String toolArguments,
        int originalChars,
        long createdAtEpochMs
) {
}
