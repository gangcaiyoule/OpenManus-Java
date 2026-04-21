package com.openmanus.aiframework.runtime.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Objects;

public record AiAgentParameterSchema(JsonNode schema) {

    public AiAgentParameterSchema {
        schema = Objects.requireNonNull(schema, "schema cannot be null");
    }

    public static AiAgentParameterSchema singleStringParameter(String name, String description) {
        String fieldName = Objects.requireNonNull(name, "name cannot be null");
        String fieldDescription = Objects.requireNonNull(description, "description cannot be null");

        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("type", "object");

        ObjectNode properties = root.putObject("properties");
        ObjectNode field = properties.putObject(fieldName);
        field.put("type", "string");
        field.put("description", fieldDescription);

        root.putArray("required").add(fieldName);
        return new AiAgentParameterSchema(root);
    }
}
