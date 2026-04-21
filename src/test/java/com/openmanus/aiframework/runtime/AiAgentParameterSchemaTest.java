package com.openmanus.aiframework.runtime;

import com.fasterxml.jackson.databind.node.TextNode;
import com.openmanus.aiframework.runtime.model.AiAgentParameterSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiAgentParameterSchemaTest {

    @Test
    void shouldBuildSingleStringParameterSchema() {
        AiAgentParameterSchema schema = AiAgentParameterSchema.singleStringParameter("context", "desc");
        assertEquals("object", schema.schema().path("type").asText());
        assertEquals("string", schema.schema().path("properties").path("context").path("type").asText());
        assertEquals("desc", schema.schema().path("properties").path("context").path("description").asText());
    }

    @Test
    void shouldValidateInputs() {
        assertThrows(NullPointerException.class, () -> new AiAgentParameterSchema(null));
        assertThrows(NullPointerException.class, () -> AiAgentParameterSchema.singleStringParameter(null, "d"));
        assertThrows(NullPointerException.class, () -> AiAgentParameterSchema.singleStringParameter("x", null));

        AiAgentParameterSchema schema = new AiAgentParameterSchema(new TextNode("plain"));
        assertEquals("plain", schema.schema().asText());
    }
}
