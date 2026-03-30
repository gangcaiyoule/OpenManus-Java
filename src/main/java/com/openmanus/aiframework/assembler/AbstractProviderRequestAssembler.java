package com.openmanus.aiframework.assembler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openmanus.aiframework.model.ChatRequestEnvelope;
import com.openmanus.aiframework.model.ChatRequestOptions;

abstract class AbstractProviderRequestAssembler implements ProviderRequestAssembler {

    protected final ObjectMapper objectMapper;

    protected AbstractProviderRequestAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    protected ObjectNode createBaseRequest(ChatRequestEnvelope request, boolean stream) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        ObjectNode root = objectMapper.createObjectNode();
        if (request.getModel() != null && !request.getModel().isBlank()) {
            root.put("model", request.getModel());
        }
        ChatRequestOptions options = request.getRequestOptions();
        if (options != null) {
            if (options.getTemperature() != null) {
                root.put("temperature", options.getTemperature());
            }
            if (options.getMaxTokens() != null) {
                root.put("max_tokens", options.getMaxTokens());
            }
        }
        root.put("stream", stream);
        return root;
    }

    protected void mergeProviderPayload(ObjectNode root, JsonNode providerPayload) {
        if (providerPayload == null || !providerPayload.isObject()) {
            return;
        }
        providerPayload.fields().forEachRemaining(entry -> root.set(entry.getKey(), entry.getValue()));
    }
}
