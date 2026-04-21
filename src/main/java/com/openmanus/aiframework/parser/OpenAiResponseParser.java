package com.openmanus.aiframework.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.openmanus.aiframework.model.AiProviderType;
import com.openmanus.aiframework.model.ChatResponseEnvelope;
import com.openmanus.aiframework.model.ProviderStreamChunk;

import java.util.ArrayList;
import java.util.List;

public class OpenAiResponseParser implements ProviderResponseParser {

    @Override
    public ChatResponseEnvelope parse(JsonNode root) {
        JsonNode choice = first(root.path("choices"));
        JsonNode message = choice.path("message");
        String content = message.path("content").asText("");
        List<JsonNode> toolCalls = collectArray(message.path("tool_calls"));

        return ChatResponseEnvelope.builder()
                .providerType(AiProviderType.OPENAI)
                .content(content)
                .toolCalls(toolCalls)
                .finishReason(choice.path("finish_reason").asText(null))
                .usage(root.path("usage").isMissingNode() ? null : root.path("usage"))
                .rawResponse(root)
                .build();
    }

    @Override
    public ProviderStreamChunk parseStreamChunk(String eventType, JsonNode chunk) {
        if ("[DONE]".equals(eventType) || chunk == null) {
            return ProviderStreamChunk.builder()
                    .completed(true)
                    .build();
        }

        // Responses API style events (e.g. response.output_text.delta / response.completed)
        String type = chunk.path("type").asText("");
        if (!type.isBlank()) {
            if ("response.output_text.delta".equals(type)) {
                String delta = chunk.path("delta").asText(null);
                return ProviderStreamChunk.builder()
                        .deltaText(delta)
                        .rawChunk(chunk)
                        .completed(false)
                        .build();
            }
            if ("response.function_call_arguments.delta".equals(type)) {
                return ProviderStreamChunk.builder()
                        .toolCall(chunk)
                        .rawChunk(chunk)
                        .completed(false)
                        .build();
            }
            if ("response.completed".equals(type)) {
                String finishReason = chunk.path("response")
                        .path("status")
                        .asText("completed");
                JsonNode usage = chunk.path("response").path("usage");
                return ProviderStreamChunk.builder()
                        .finishReason(finishReason)
                        .usage(usage.isMissingNode() ? null : usage)
                        .rawChunk(chunk)
                        .completed(true)
                        .build();
            }
        }

        JsonNode choice = first(chunk.path("choices"));
        JsonNode delta = choice.path("delta");

        String text = delta.path("content").isMissingNode() ? null : delta.path("content").asText(null);
        List<JsonNode> toolCalls = collectArray(delta.path("tool_calls"));
        String finishReason = choice.path("finish_reason").asText(null);

        return ProviderStreamChunk.builder()
                .deltaText(text)
                .toolCalls(toolCalls)
                .finishReason(finishReason)
                .rawChunk(chunk)
                .completed("[DONE]".equals(eventType) || finishReason != null)
                .build();
    }

    private JsonNode first(JsonNode node) {
        return node.isArray() && !node.isEmpty() ? node.get(0) : node;
    }

    private List<JsonNode> collectArray(JsonNode node) {
        List<JsonNode> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(list::add);
        }
        return list;
    }
}
