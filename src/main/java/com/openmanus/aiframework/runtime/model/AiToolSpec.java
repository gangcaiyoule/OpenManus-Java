package com.openmanus.aiframework.runtime.model;

import com.fasterxml.jackson.databind.JsonNode;

public record AiToolSpec(
        String name,
        String description,
        JsonNode inputSchema
) {

    public AiToolSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("tool spec name cannot be blank");
        }
        description = description == null ? "" : description;
    }
}
