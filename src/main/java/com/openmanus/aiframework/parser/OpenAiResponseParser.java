package com.openmanus.aiframework.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.openmanus.aiframework.exception.AiFrameworkException;
import com.openmanus.aiframework.model.AiProviderType;
import com.openmanus.aiframework.model.ChatResponseEnvelope;
import com.openmanus.aiframework.model.ProviderStreamChunk;

import java.util.ArrayList;
import java.util.List;

public class OpenAiResponseParser implements ProviderResponseParser {

    @Override
    public ChatResponseEnvelope parse(JsonNode root) {
        JsonNode error = root == null ? null : root.path("error");
        if (error != null && error.isObject() && !error.isEmpty()) {
            throw new AiFrameworkException("Provider returned error payload: " + summarizeError(error));
        }
        JsonNode choice = first(root.path("choices"));
        JsonNode message = choice.path("message");
        String content = extractContent(message.path("content"));
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
        JsonNode error = chunk.path("error");
        if (error.isObject() && !error.isEmpty()) {
            throw new AiFrameworkException("Provider returned error payload: " + summarizeError(error));
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

    private String extractContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return "";
        }
        if (contentNode.isTextual()) {
            return contentNode.asText("");
        }
        if (!contentNode.isArray()) {
            return contentNode.asText("");
        }

        StringBuilder content = new StringBuilder();
        for (JsonNode item : contentNode) {
            if (item == null || item.isNull()) {
                continue;
            }
            String text = item.path("text").asText("");
            if (text.isBlank() && item.path("text").isObject()) {
                text = item.path("text").path("value").asText("");
            }
            if (!text.isBlank()) {
                content.append(text);
            }
        }
        return content.toString();
    }

    private String summarizeError(JsonNode error) {
        String message = error.path("message").asText("");
        String type = error.path("type").asText("");
        String code = error.path("code").asText("");
        List<String> parts = new ArrayList<>();
        if (!type.isBlank()) {
            parts.add("type=" + type);
        }
        if (!code.isBlank()) {
            parts.add("code=" + code);
        }
        if (!message.isBlank()) {
            parts.add("message=" + message);
        }
        return parts.isEmpty() ? error.toString() : String.join(", ", parts);
    }
}
