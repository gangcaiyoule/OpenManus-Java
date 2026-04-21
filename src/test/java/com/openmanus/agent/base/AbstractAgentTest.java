package com.openmanus.agent.base;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.aiframework.runtime.model.AiAgentParameterSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AbstractAgentTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldBuildSingleParameterSchemaWithRuntimeType() {
        DummyAgent agent = DummyAgent.builder()
                .name("runtime_name")
                .description("runtime_desc")
                .singleParameter("user context")
                .build();

        JsonNode schema = agent.parameters().schema();
        assertEquals("object", schema.path("type").asText());
        assertEquals("string", schema.path("properties").path("context").path("type").asText());
        assertEquals("user context", schema.path("properties").path("context").path("description").asText());
        assertEquals("context", schema.path("required").get(0).asText());
    }

    @Test
    void shouldKeepFirstAssignedValuesInBuilder() throws Exception {
        AiAgentParameterSchema first = AiAgentParameterSchema.singleStringParameter("context", "first");
        AiAgentParameterSchema second = AiAgentParameterSchema.singleStringParameter("context", "second");

        DummyAgent agent = DummyAgent.builder()
                .name("first_name")
                .name("second_name")
                .description("first_desc")
                .description("second_desc")
                .parameters(first)
                .parameters(second)
                .build();

        assertEquals("first_name", agent.name());
        assertEquals("first_desc", agent.description());
        assertEquals("first", agent.parameters().schema()
                .path("properties").path("context").path("description").asText());

        assertEquals(
                OBJECT_MAPPER.writeValueAsString(first.schema()),
                OBJECT_MAPPER.writeValueAsString(agent.parameters().schema())
        );
    }

    @Test
    void shouldRejectNullSchemaInput() {
        assertThrows(NullPointerException.class, () -> DummyAgent.builder()
                .name("n")
                .description("d")
                .parameters(null)
                .build());
    }

    static class DummyAgent extends AbstractAgent<DummyAgent.Builder> {

        static class Builder extends AbstractAgent.Builder<Builder> {
            DummyAgent build() {
                return new DummyAgent(this);
            }
        }

        static Builder builder() {
            return new Builder();
        }

        DummyAgent(Builder builder) {
            super(builder);
        }
    }
}
