package com.openmanus.aiframework.runtime.model;

import java.util.List;
import java.util.Objects;

public record AiChatMessage(
        Role role,
        String content,
        String name,
        String toolCallId,
        List<AiToolCall> toolCalls
) {

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT,
        TOOL
    }

    public AiChatMessage {
        role = Objects.requireNonNull(role, "role cannot be null");
        content = content == null ? "" : content;
        name = (name == null || name.isBlank()) ? null : name;
        toolCallId = (toolCallId == null || toolCallId.isBlank()) ? null : toolCallId;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static AiChatMessage system(String content) {
        return new AiChatMessage(Role.SYSTEM, content, null, null, List.of());
    }

    public static AiChatMessage user(String content) {
        return new AiChatMessage(Role.USER, content, null, null, List.of());
    }

    public static AiChatMessage assistant(String content) {
        return new AiChatMessage(Role.ASSISTANT, content, null, null, List.of());
    }

    public static AiChatMessage assistant(String content, List<AiToolCall> toolCalls) {
        return new AiChatMessage(Role.ASSISTANT, content, null, null, toolCalls);
    }

    public static AiChatMessage tool(AiToolResult result) {
        Objects.requireNonNull(result, "result cannot be null");
        return new AiChatMessage(
                Role.TOOL,
                result.content(),
                result.toolName(),
                result.toolCallId(),
                List.of()
        );
    }
}
