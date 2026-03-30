package com.openmanus.aiframework.assembler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openmanus.aiframework.model.ChatMessage;
import com.openmanus.aiframework.model.ChatRequestEnvelope;

public class OpenAiRequestAssembler extends AbstractProviderRequestAssembler {

    public OpenAiRequestAssembler(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public JsonNode assemble(ChatRequestEnvelope request, boolean stream) {
        ObjectNode root = createBaseRequest(request, stream);
        ArrayNode messages = root.putArray("messages");
        for (ChatMessage message : request.getMessages()) {
            ObjectNode item = messages.addObject();
            item.put("role", message.getRole());
            item.put("content", message.getContent());
            if (message.getName() != null && !message.getName().isBlank()) {
                item.put("name", message.getName());
            }
            if (message.getToolCallId() != null && !message.getToolCallId().isBlank()) {
                item.put("tool_call_id", message.getToolCallId());
            }
            if (message.getToolCalls() != null && message.getToolCalls().isArray()) {
                item.set("tool_calls", message.getToolCalls().deepCopy());
            }
        }
        mergeProviderPayload(root, request.getProviderPayload());
        return root;
    }
}
