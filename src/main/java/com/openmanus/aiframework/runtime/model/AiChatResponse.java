package com.openmanus.aiframework.runtime.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

public record AiChatResponse(
        AiChatMessage message,
        AiFinishReason finishReason,
        AiTokenUsage tokenUsage,
        String responseId,
        String model,
        JsonNode rawResponse
) {

    public AiChatResponse {
        message = Objects.requireNonNull(message, "message cannot be null");
        responseId = (responseId == null || responseId.isBlank()) ? null : responseId;
        model = (model == null || model.isBlank()) ? null : model;
    }
}
