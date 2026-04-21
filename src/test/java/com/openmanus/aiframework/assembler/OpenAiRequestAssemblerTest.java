package com.openmanus.aiframework.assembler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.aiframework.model.AiProviderType;
import com.openmanus.aiframework.model.ChatMessage;
import com.openmanus.aiframework.model.ChatRequestEnvelope;
import com.openmanus.aiframework.model.ChatRequestOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiRequestAssemblerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldAssembleMessagesAndPassThroughToolsAndSchema() throws Exception {
        JsonNode providerPayload = objectMapper.readTree("""
                {
                  "tools": [{"type":"function","function":{"name":"search","parameters":{"type":"object"}}}],
                  "response_format": {
                    "type": "json_schema",
                    "json_schema": {"name":"answer","schema":{"type":"object"}}
                  }
                }
                """);
        JsonNode toolCalls = objectMapper.readTree("""
                [
                  {
                    "id":"call_abc",
                    "type":"function",
                    "function":{"name":"search","arguments":"{\\"q\\":\\"weather\\"}"}
                  }
                ]
                """);

        ChatRequestEnvelope request = ChatRequestEnvelope.builder()
                .providerType(AiProviderType.OPENAI)
                .model("gpt-4o-mini")
                .message(ChatMessage.builder().role("user").content("hi").build())
                .message(ChatMessage.builder().role("assistant").content("").toolCalls(toolCalls).build())
                .message(ChatMessage.builder().role("tool").content("{\"ok\":true}").toolCallId("call_abc").build())
                .requestOptions(ChatRequestOptions.builder().temperature(0.3).maxTokens(64).build())
                .providerPayload(providerPayload)
                .build();

        OpenAiRequestAssembler assembler = new OpenAiRequestAssembler(objectMapper);
        JsonNode root = assembler.assemble(request, false);

        assertEquals("gpt-4o-mini", root.path("model").asText());
        assertEquals(0.3, root.path("temperature").asDouble());
        assertEquals(64, root.path("max_tokens").asInt());
        assertEquals("user", root.path("messages").get(0).path("role").asText());
        assertEquals("call_abc", root.path("messages").get(1).path("tool_calls").get(0).path("id").asText());
        assertEquals("call_abc", root.path("messages").get(2).path("tool_call_id").asText());
        assertTrue(root.path("tools").isArray());
        assertEquals("json_schema", root.path("response_format").path("type").asText());
    }
}
