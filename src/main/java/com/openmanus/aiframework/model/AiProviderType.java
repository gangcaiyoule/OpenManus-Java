package com.openmanus.aiframework.model;

public enum AiProviderType {
    OPENAI,
    ANTHROPIC,
    GEMINI;

    public static AiProviderType from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("provider type cannot be blank");
        }
        return AiProviderType.valueOf(value.trim().toUpperCase());
    }
}
