package com.openmanus.aiframework.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.openmanus.aiframework.model.AiProviderType;
import com.openmanus.aiframework.model.ChatResponseEnvelope;
import com.openmanus.aiframework.model.ProviderStreamChunk;

import java.util.ArrayList;
import java.util.List;

public class AnthropicResponseParser implements ProviderResponseParser {

    @Override
    public ChatResponseEnvelope parse(JsonNode root) {
        StringBuilder content = new StringBuilder();
        List<JsonNode> toolCalls = new ArrayList<>();

        JsonNode contentArray = root.path("content");
        if (contentArray.isArray()) {
            for (JsonNode block : contentArray) {
                String type = block.path("type").asText("");
                if ("text".equals(type)) {
                    content.append(block.path("text").asText(""));
                }
                if ("tool_use".equals(type)) {
                    toolCalls.add(block);
                }
            }
        }

        return ChatResponseEnvelope.builder()
                .providerType(AiProviderType.ANTHROPIC)
                .content(content.toString())
                .toolCalls(toolCalls)
                .finishReason(root.path("stop_reason").asText(null))
                .usage(root.path("usage").isMissingNode() ? null : root.path("usage"))
                .rawResponse(root)
                .build();
    }

    @Override
    public ProviderStreamChunk parseStreamChunk(String eventType, JsonNode chunk) {
        String normalizedType = eventType == null || eventType.isBlank()
                ? chunk.path("type").asText("")
                : eventType;

        String delta = null;
        List<JsonNode> toolCalls = new ArrayList<>();
        boolean completed = false;
        String finishReason = null;

        if ("content_block_delta".equals(normalizedType)) {
            JsonNode d = chunk.path("delta");
            if ("text_delta".equals(d.path("type").asText(""))) {
                delta = d.path("text").asText(null);
            }
        }
        if ("content_block_start".equals(normalizedType)
                && "tool_use".equals(chunk.path("content_block").path("type").asText(""))) {
            toolCalls.add(chunk.path("content_block"));
        }
        if ("message_delta".equals(normalizedType)) {
            finishReason = chunk.path("delta").path("stop_reason").asText(null);
        }
        if ("message_stop".equals(normalizedType)) {
            completed = true;
        }

        return ProviderStreamChunk.builder()
                .deltaText(delta)
                .toolCalls(toolCalls)
                .finishReason(finishReason)
                .rawChunk(chunk)
                .completed(completed)
                .build();
    }
}
