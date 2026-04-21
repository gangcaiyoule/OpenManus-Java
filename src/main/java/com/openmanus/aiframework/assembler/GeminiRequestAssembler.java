package com.openmanus.aiframework.assembler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openmanus.aiframework.model.ChatMessage;
import com.openmanus.aiframework.model.ChatRequestEnvelope;

public class GeminiRequestAssembler extends AbstractProviderRequestAssembler {

    public GeminiRequestAssembler(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public JsonNode assemble(ChatRequestEnvelope request, boolean stream) {
        ObjectNode root = objectMapper.createObjectNode();
        String systemText = collectSystemText(request);
        if (!systemText.isBlank()) {
            ObjectNode systemInstruction = root.putObject("systemInstruction");
            ArrayNode parts = systemInstruction.putArray("parts");
            parts.addObject().put("text", systemText);
        }

        ArrayNode contents = root.putArray("contents");
        for (ChatMessage message : request.getMessages()) {
            if (message == null || message.getRole() == null || message.getRole().isBlank()) {
                continue;
            }
            String role = message.getRole().toLowerCase();
            if ("system".equals(role)) {
                continue;
            }

            if ("tool".equals(role)) {
                contents.add(buildToolResponseMessage(message));
                continue;
            }

            if ("assistant".equals(role) && message.getToolCalls() != null && message.getToolCalls().isArray()
                    && !message.getToolCalls().isEmpty()) {
                contents.add(buildAssistantToolCallMessage(message));
                continue;
            }

            ObjectNode messageNode = contents.addObject();
            messageNode.put("role", normalizeRole(role));
            ArrayNode parts = messageNode.putArray("parts");
            parts.addObject().put("text", message.getContent() == null ? "" : message.getContent());
        }

        ObjectNode generationConfig = root.putObject("generationConfig");
        if (request.getRequestOptions() != null) {
            if (request.getRequestOptions().getTemperature() != null) {
                generationConfig.put("temperature", request.getRequestOptions().getTemperature());
            }
            if (request.getRequestOptions().getMaxTokens() != null) {
                generationConfig.put("maxOutputTokens", request.getRequestOptions().getMaxTokens());
            }
        }

        JsonNode providerPayload = request.getProviderPayload();
        if (providerPayload != null && providerPayload.isObject()) {
            JsonNode payloadGenerationConfig = providerPayload.path("generationConfig");
            if (payloadGenerationConfig.isObject()) {
                payloadGenerationConfig.fields().forEachRemaining(entry ->
                        generationConfig.set(entry.getKey(), entry.getValue()));
            }
            providerPayload.fields().forEachRemaining(entry -> {
                if (!"generationConfig".equals(entry.getKey())) {
                    root.set(entry.getKey(), entry.getValue());
                }
            });
        }
        return root;
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return "user";
        }
        return switch (role.toLowerCase()) {
            case "assistant" -> "model";
            case "system" -> "user";
            default -> role.toLowerCase();
        };
    }

    private String collectSystemText(ChatRequestEnvelope request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (ChatMessage message : request.getMessages()) {
            if (message == null || message.getRole() == null) {
                continue;
            }
            if (!"system".equalsIgnoreCase(message.getRole())) {
                continue;
            }
            if (message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(message.getContent());
        }
        return builder.toString();
    }

    private ObjectNode buildAssistantToolCallMessage(ChatMessage message) {
        ObjectNode messageNode = objectMapper.createObjectNode();
        messageNode.put("role", "model");
        ArrayNode parts = messageNode.putArray("parts");
        if (message.getContent() != null && !message.getContent().isBlank()) {
            parts.addObject().put("text", message.getContent());
        }
        for (JsonNode toolCall : message.getToolCalls()) {
            String name = toolCall.path("functionCall").path("name").asText(null);
            if (name == null || name.isBlank()) {
                name = toolCall.path("function").path("name").asText(null);
            }
            if (name == null || name.isBlank()) {
                name = toolCall.path("name").asText(null);
            }
            if (name == null || name.isBlank()) {
                continue;
            }
            ObjectNode part = parts.addObject();
            ObjectNode functionCall = part.putObject("functionCall");
            functionCall.put("name", name);
            JsonNode args = toolCall.path("functionCall").path("args");
            if (args.isMissingNode() || args.isNull()) {
                args = toolCall.path("function").path("arguments");
            }
            if (args.isMissingNode() || args.isNull()) {
                args = toolCall.path("input");
            }
            functionCall.set("args", toObjectNode(args));
        }
        return messageNode;
    }

    private ObjectNode buildToolResponseMessage(ChatMessage message) {
        ObjectNode messageNode = objectMapper.createObjectNode();
        messageNode.put("role", "user");
        ArrayNode parts = messageNode.putArray("parts");
        ObjectNode functionResponse = parts.addObject().putObject("functionResponse");
        functionResponse.put("name", message.getName() == null ? "" : message.getName());
        ObjectNode response = functionResponse.putObject("response");
        response.put("content", message.getContent() == null ? "" : message.getContent());
        if (message.getToolCallId() != null && !message.getToolCallId().isBlank()) {
            response.put("toolCallId", message.getToolCallId());
        }
        return messageNode;
    }

    private ObjectNode toObjectNode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return objectMapper.createObjectNode();
        }
        if (node.isObject()) {
            return (ObjectNode) node.deepCopy();
        }
        if (node.isTextual()) {
            try {
                JsonNode parsed = objectMapper.readTree(node.asText());
                if (parsed.isObject()) {
                    return (ObjectNode) parsed;
                }
            } catch (Exception ignored) {
            }
        }
        ObjectNode wrapped = objectMapper.createObjectNode();
        wrapped.set("value", node.deepCopy());
        return wrapped;
    }
}
