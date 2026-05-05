package com.openmanus.aiframework.runtime.model;

public record AiTokenUsage(
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens
) {
}
