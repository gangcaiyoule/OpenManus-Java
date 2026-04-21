package com.openmanus.agent.base;

import com.openmanus.aiframework.runtime.model.AiAgentParameterSchema;

import java.util.Objects;

public abstract class AbstractAgent<B extends AbstractAgent.Builder<B>> {

    public static abstract class Builder<B extends Builder<B>> {

        private String name;
        private String description;
        private AiAgentParameterSchema parameters;

        @SuppressWarnings("unchecked")
        protected B result() {
            return (B) this;
        }

        public B name(String name) {
            if (this.name == null) {
                this.name = name;
            }
            return result();
        }

        public B description(String description) {
            if (this.description == null) {
                this.description = description;
            }
            return result();
        }

        public B parameters(AiAgentParameterSchema parameters) {
            if (this.parameters == null) {
                this.parameters = parameters;
            }
            return result();
        }

        public B singleParameter(String context) {
            if (this.parameters == null) {
                this.parameters = AiAgentParameterSchema.singleStringParameter(
                        "context",
                        Objects.requireNonNull(context, "context cannot be null")
                );
            }
            return result();
        }
    }

    private final String name;
    private final String description;
    private final AiAgentParameterSchema parameters;

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public AiAgentParameterSchema parameters() {
        return parameters;
    }

    public AbstractAgent(Builder<B> builder) {
        this.name = Objects.requireNonNull(builder.name, "name cannot be null");
        this.description = Objects.requireNonNull(builder.description, "description cannot be null");
        this.parameters = Objects.requireNonNull(builder.parameters, "parameters cannot be null");
    }
}
