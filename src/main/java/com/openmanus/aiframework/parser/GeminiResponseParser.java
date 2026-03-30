package com.openmanus.aiframework.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.openmanus.aiframework.model.AiProviderType;
import com.openmanus.aiframework.model.ChatResponseEnvelope;
import com.openmanus.aiframework.model.ProviderStreamChunk;

import java.util.ArrayList;
import java.util.List;

public class GeminiResponseParser implements ProviderResponseParser {

    @Override
    public ChatResponseEnvelope parse(JsonNode root) {
        JsonNode candidate = first(root.path("candidates"));
        JsonNode parts = candidate.path("content").path("parts");

        StringBuilder content = new StringBuilder();
        List<JsonNode> toolCalls = new ArrayList<>();
        if (parts.isArray()) {
            for (JsonNode part : parts) {
                if (part.has("text")) {
                    content.append(part.path("text").asText(""));
                }
                if (part.has("functionCall")) {
                    toolCalls.add(part.path("functionCall"));
                }
            }
        }

        return ChatResponseEnvelope.builder()
                .providerType(AiProviderType.GEMINI)
                .content(content.toString())
                .toolCalls(toolCalls)
                .finishReason(candidate.path("finishReason").asText(null))
                .usage(root.path("usageMetadata").isMissingNode() ? null : root.path("usageMetadata"))
                .rawResponse(root)
                .build();
    }

    @Override
    public ProviderStreamChunk parseStreamChunk(String eventType, JsonNode chunk) {
        JsonNode candidate = first(chunk.path("candidates"));
        JsonNode parts = candidate.path("content").path("parts");

        StringBuilder delta = new StringBuilder();
        List<JsonNode> toolCalls = new ArrayList<>();
        if (parts.isArray()) {
            for (JsonNode part : parts) {
                if (part.has("text")) {
                    delta.append(part.path("text").asText(""));
                }
                if (part.has("functionCall")) {
                    toolCalls.add(part.path("functionCall"));
                }
            }
        }

        String finishReason = candidate.path("finishReason").asText(null);
        return ProviderStreamChunk.builder()
                .deltaText(delta.isEmpty() ? null : delta.toString())
                .toolCalls(toolCalls)
                .finishReason(finishReason)
                .usage(chunk.path("usageMetadata").isMissingNode() ? null : chunk.path("usageMetadata"))
                .rawChunk(chunk)
                .completed(finishReason != null)
                .build();
    }

    private JsonNode first(JsonNode node) {
        return node.isArray() && !node.isEmpty() ? node.get(0) : node;
    }
}
