package com.openmanus.aiframework.tool;

/**
 * Runtime representation of one tool call emitted by model output.
 */
public record AiToolExecutionRequest(
        String id,
        String name,
        String arguments
) {

    public AiToolExecutionRequest {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("tool name cannot be null or blank");
        }
        id = normalize(id);
        arguments = normalizeArguments(arguments);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeArguments(String value) {
        if (value == null || value.isBlank()) {
            return "{}";
        }
        return value;
    }
}
