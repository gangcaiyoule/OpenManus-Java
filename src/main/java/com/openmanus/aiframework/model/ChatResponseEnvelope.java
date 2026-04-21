package com.openmanus.aiframework.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder(toBuilder = true)
public class ChatResponseEnvelope {
    AiProviderType providerType;
    String content;
    @Singular
    List<JsonNode> toolCalls;
    String finishReason;
    JsonNode usage;
    JsonNode rawResponse;
}
