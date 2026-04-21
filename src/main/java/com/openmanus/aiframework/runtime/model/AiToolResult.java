package com.openmanus.aiframework.runtime.model;

public record AiToolResult(
        String toolCallId,
        String toolName,
        String content,
        boolean error
) {

    public AiToolResult {
        if (toolCallId == null || toolCallId.isBlank()) {
            throw new IllegalArgumentException("toolCallId cannot be blank");
        }
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName cannot be blank");
        }
        content = content == null ? "" : content;
    }
}
