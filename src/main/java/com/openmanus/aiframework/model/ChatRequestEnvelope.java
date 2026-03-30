package com.openmanus.aiframework.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ChatRequestEnvelope {
    AiProviderType providerType;
    String model;
    @Singular
    List<ChatMessage> messages;
    JsonNode providerPayload;
    ChatRequestOptions requestOptions;
}
