package com.openmanus.aiframework.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChatRequestOptions {
    @Builder.Default
    Integer timeoutSeconds = 120;
    @Builder.Default
    Integer maxRetries = 1;
    Double temperature;
    Integer maxTokens;
    @Builder.Default
    boolean stream = false;
}
