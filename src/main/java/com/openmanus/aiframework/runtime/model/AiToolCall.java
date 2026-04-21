package com.openmanus.aiframework.runtime.model;

import java.util.Objects;

public record AiToolCall(
        String id,
        String name,
        String arguments
) {

    public AiToolCall {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("tool call name cannot be blank");
        }
        arguments = (arguments == null || arguments.isBlank()) ? "{}" : arguments;
        id = (id == null || id.isBlank()) ? null : id;
    }

    public AiToolCall withId(String toolCallId) {
        String normalizedId = Objects.requireNonNull(toolCallId, "toolCallId cannot be null");
        if (normalizedId.isBlank()) {
            throw new IllegalArgumentException("toolCallId cannot be blank");
        }
        return new AiToolCall(normalizedId, name, arguments);
    }
}
