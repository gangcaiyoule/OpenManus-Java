package com.openmanus.aiframework.model;

public enum AiProviderType {
    OPENAI,
    ANTHROPIC,
    GEMINI;

    public static AiProviderType from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("provider type cannot be blank");
        }
        String normalized = value.trim().toLowerCase()
                .replace("-", "")
                .replace("_", "")
                .replace(" ", "");
        return switch (normalized) {
            case "openai", "openaicompatible" -> OPENAI;
            case "anthropic" -> ANTHROPIC;
            case "gemini", "google" -> GEMINI;
            default -> throw new IllegalArgumentException("unsupported provider type: " + value);
        };
    }
}
