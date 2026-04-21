package com.openmanus.aiframework.runtime.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Objects;

public record AiChatRequest(
        String model,
        List<AiChatMessage> messages,
        List<AiToolSpec> toolSpecs,
        Double temperature,
        Integer maxOutputTokens,
        Integer timeoutSeconds,
        JsonNode responseFormat
) {

    public AiChatRequest {
        model = model == null ? "" : model;
        messages = messages == null
                ? List.of()
                : messages.stream().filter(Objects::nonNull).toList();
        toolSpecs = toolSpecs == null
                ? List.of()
                : toolSpecs.stream().filter(Objects::nonNull).toList();
    }
}
