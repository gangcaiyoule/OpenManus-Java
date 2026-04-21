package com.openmanus.aiframework.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ProviderStreamChunk {
    String deltaText;
    @Singular
    List<JsonNode> toolCalls;
    String finishReason;
    JsonNode usage;
    JsonNode rawChunk;
    @Builder.Default
    boolean completed = false;
}
