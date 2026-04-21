package com.openmanus.aiframework.assembler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openmanus.aiframework.model.ChatMessage;
import com.openmanus.aiframework.model.ChatRequestEnvelope;

public class AnthropicRequestAssembler extends AbstractProviderRequestAssembler {

    public AnthropicRequestAssembler(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public JsonNode assemble(ChatRequestEnvelope request, boolean stream) {
        ObjectNode root = createBaseRequest(request, stream);
        if (!root.has("max_tokens")) {
            root.put("max_tokens", 1024);
        }

        String systemText = collectSystemText(request);
        if (!systemText.isBlank()) {
            root.put("system", systemText);
        }

        ArrayNode messages = root.putArray("messages");
        for (ChatMessage message : request.getMessages()) {
            if (message == null || message.getRole() == null || message.getRole().isBlank()) {
                continue;
            }
            String role = message.getRole().toLowerCase();
            if ("system".equals(role)) {
                continue;
            }

            if ("tool".equals(role)) {
                messages.add(buildToolResultMessage(message));
                continue;
            }

            if ("assistant".equals(role) && message.getToolCalls() != null && message.getToolCalls().isArray()
                    && !message.getToolCalls().isEmpty()) {
                messages.add(buildAssistantToolUseMessage(message));
                continue;
            }

            ObjectNode item = messages.addObject();
            item.put("role", normalizeRole(role));
            item.put("content", message.getContent() == null ? "" : message.getContent());
        }

        mergeProviderPayload(root, request.getProviderPayload());
        return root;
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

    private ObjectNode buildToolResultMessage(ChatMessage message) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("role", "user");
        ArrayNode content = item.putArray("content");
        ObjectNode block = content.addObject();
        block.put("type", "tool_result");
        block.put("tool_use_id", message.getToolCallId() == null ? "" : message.getToolCallId());
        block.put("content", message.getContent() == null ? "" : message.getContent());
        return item;
    }

    private ObjectNode buildAssistantToolUseMessage(ChatMessage message) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("role", "assistant");
        ArrayNode content = item.putArray("content");
        if (message.getContent() != null && !message.getContent().isBlank()) {
            ObjectNode text = content.addObject();
            text.put("type", "text");
            text.put("text", message.getContent());
        }
        for (JsonNode toolCall : message.getToolCalls()) {
            String name = toolCall.path("function").path("name").asText(null);
            if (name == null || name.isBlank()) {
                name = toolCall.path("name").asText(null);
            }
            if (name == null || name.isBlank()) {
                continue;
            }
            ObjectNode block = content.addObject();
            block.put("type", "tool_use");
            String id = toolCall.path("id").asText(null);
            block.put("id", id == null ? "" : id);
            block.put("name", name);
            JsonNode input = toolCall.path("function").path("arguments");
            if (input.isMissingNode() || input.isNull()) {
                input = toolCall.path("input");
            }
            block.set("input", toObjectNode(input));
        }
        return item;
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

    private String normalizeRole(String role) {
        if ("assistant".equals(role)) {
            return "assistant";
        }
        return "user";
    }
}
