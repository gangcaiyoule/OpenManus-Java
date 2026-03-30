package com.openmanus.aiframework.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChatMessage {
    String role;
    String content;
    String name;
    String toolCallId;
    JsonNode toolCalls;
}
