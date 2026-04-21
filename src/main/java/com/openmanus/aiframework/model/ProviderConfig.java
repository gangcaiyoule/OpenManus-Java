package com.openmanus.aiframework.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProviderConfig {
    AiProviderType providerType;
    String baseUrl;
    String apiKey;
    String model;
    Integer timeoutSeconds;
    Integer maxRetries;
}
