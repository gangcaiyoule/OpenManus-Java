package com.openmanus.aiframework.tool;

import com.openmanus.aiframework.runtime.model.AiAgentParameterSchema;
import com.openmanus.aiframework.runtime.model.AiToolSpec;

import java.util.Objects;

/**
 * One fully-registered tool with schema and executor.
 */
public record AiRegisteredTool(
        String name,
        String description,
        AiAgentParameterSchema parameters,
        AiToolExecutor executor
) {

    public AiRegisteredTool {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("tool name cannot be null or blank");
        }
        description = description == null ? "" : description;
        parameters = Objects.requireNonNull(parameters, "parameters cannot be null");
        executor = Objects.requireNonNull(executor, "executor cannot be null");
    }

    public AiToolSpec toRuntimeToolSpec() {
        return new AiToolSpec(name, description, parameters.schema());
    }
}
