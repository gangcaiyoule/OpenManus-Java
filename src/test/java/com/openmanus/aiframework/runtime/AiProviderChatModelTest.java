package com.openmanus.aiframework.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.aiframework.api.AiProviderClient;
import com.openmanus.aiframework.api.StreamListener;
import com.openmanus.aiframework.config.AiProviderClientRegistry;
import com.openmanus.aiframework.exception.AiFrameworkException;
import com.openmanus.aiframework.model.AiProviderType;
import com.openmanus.aiframework.model.ChatRequestEnvelope;
import com.openmanus.aiframework.model.ChatResponseEnvelope;
import com.openmanus.aiframework.runtime.model.AiChatMessage;
import com.openmanus.aiframework.runtime.model.AiChatRequest;
import com.openmanus.aiframework.runtime.model.AiFinishReason;
import com.openmanus.aiframework.runtime.model.AiToolCall;
import com.openmanus.aiframework.runtime.model.AiToolSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiProviderChatModelTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldMapOpenAiPayloadAndResponse() throws Exception {
        CapturingClient client = new CapturingClient();
        client.response = ChatResponseEnvelope.builder()
                .providerType(AiProviderType.OPENAI)
                .content("ok")
                .toolCall(objectMapper.readTree("""
                        {
                          "id":"call_1",
                          "type":"function",
                          "function":{"name":"search","arguments":"{\\\"q\\\":\\\"today\\\"}"}
                        }
                        """))
                .finishReason("tool_calls")
                .usage(objectMapper.readTree("""
                        {"prompt_tokens":8,"completion_tokens":4,"total_tokens":12}
                        """))
                .rawResponse(objectMapper.readTree("""
                        {"id":"resp_1","model":"gpt-5.4"}
                        """))
                .build();

        AiProviderChatModel model = new AiProviderChatModel(
                new AiProviderClientRegistry(Map.of(AiProviderType.OPENAI, client)),
                objectMapper,
                "gpt-5.4",
                0.2,
                512,
                60,
                AiProviderType.OPENAI
        );

        var responseFormat = objectMapper.readTree("""
                {
                  "type":"JSON",
                  "jsonSchema":{"name":"final_answer","rootElement":{"type":"object"}}
                }
                """);

        AiChatRequest request = new AiChatRequest(
                "gpt-5.4",
                List.of(
                        AiChatMessage.system("sys"),
                        AiChatMessage.user("weather?"),
                        AiChatMessage.assistant("call", List.of(new AiToolCall("call_1", "search", "{\"q\":\"today\"}"))),
                        new AiChatMessage(AiChatMessage.Role.TOOL, "sunny", "search", "call_1", List.of())
                ),
                List.of(new AiToolSpec("search", "Search web", objectMapper.readTree("""
                        {"type":"object","properties":{"q":{"type":"string"}},"required":["q"]}
                        """))),
                0.1,
                256,
                null,
                responseFormat
        );

        var response = model.chat(request);

        assertNotNull(client.capturedRequest);
        assertEquals("assistant", client.capturedRequest.getMessages().get(2).getRole());
        assertEquals("search", client.capturedRequest.getProviderPayload().path("tools")
                .get(0).path("function").path("name").asText());
        assertEquals("json_schema", client.capturedRequest.getProviderPayload().path("response_format")
                .path("type").asText());
        assertEquals(AiFinishReason.TOOL_CALLS, response.finishReason());
        assertEquals("resp_1", response.responseId());
        assertEquals("gpt-5.4", response.model());
        assertEquals("search", response.message().toolCalls().get(0).name());
        assertEquals(12, response.tokenUsage().totalTokens());
    }

    @Test
    void shouldMapAnthropicAndGeminiProviderPayload() throws Exception {
        CapturingClient anthropicClient = new CapturingClient();
        anthropicClient.response = ChatResponseEnvelope.builder()
                .providerType(AiProviderType.ANTHROPIC)
                .content("")
                .finishReason("tool_use")
                .build();

        var responseFormat = objectMapper.readTree("""
                {
                  "type":"json",
                  "jsonSchema":{"name":"final_answer","rootElement":{"type":"object"}}
                }
                """);
        AiChatRequest anthropicRequest = new AiChatRequest(
                "claude-3-5-sonnet-latest",
                List.of(AiChatMessage.user("hi")),
                List.of(new AiToolSpec("search", "Search web", objectMapper.readTree("""
                        {"type":"object"}
                        """))),
                null,
                null,
                null,
                responseFormat
        );

        AiProviderChatModel anthropicModel = new AiProviderChatModel(
                new AiProviderClientRegistry(Map.of(AiProviderType.ANTHROPIC, anthropicClient)),
                objectMapper,
                "claude-3-5-sonnet-latest",
                0.2,
                512,
                60,
                AiProviderType.ANTHROPIC
        );

        anthropicModel.chat(anthropicRequest);
        assertEquals("structured_output",
                anthropicClient.capturedRequest.getProviderPayload().path("tool_choice").path("name").asText());

        CapturingClient geminiClient = new CapturingClient();
        geminiClient.response = ChatResponseEnvelope.builder()
                .providerType(AiProviderType.GEMINI)
                .content("ok")
                .usage(objectMapper.readTree("""
                        {"promptTokenCount":"10","candidatesTokenCount":"3","totalTokenCount":"13"}
                        """))
                .rawResponse(objectMapper.readTree("""
                        {"response":{"id":"nested_1"},"modelVersion":"gemini-2.5-flash"}
                        """))
                .build();

        AiProviderChatModel geminiModel = new AiProviderChatModel(
                new AiProviderClientRegistry(Map.of(AiProviderType.GEMINI, geminiClient)),
                objectMapper,
                "gemini-2.5-flash",
                0.2,
                512,
                60,
                AiProviderType.GEMINI
        );

        var geminiResponse = geminiModel.chat(anthropicRequest);
        assertEquals("application/json",
                geminiClient.capturedRequest.getProviderPayload().path("generationConfig")
                        .path("responseMimeType").asText());
        assertEquals("nested_1", geminiResponse.responseId());
        assertEquals("gemini-2.5-flash", geminiResponse.model());
        assertEquals(13, geminiResponse.tokenUsage().totalTokens());
    }

    @Test
    void shouldUseRequestTimeoutWhenProvided() {
        CapturingClient client = new CapturingClient();
        client.response = ChatResponseEnvelope.builder()
                .providerType(AiProviderType.OPENAI)
                .content("ok")
                .build();

        AiProviderChatModel model = new AiProviderChatModel(
                new AiProviderClientRegistry(Map.of(AiProviderType.OPENAI, client)),
                objectMapper,
                "gpt-5.4",
                0.2,
                512,
                60,
                AiProviderType.OPENAI
        );

        AiChatRequest request = new AiChatRequest(
                "",
                List.of(AiChatMessage.user("hello")),
                List.of(),
                null,
                null,
                10,
                null
        );

        model.chat(request);

        assertNotNull(client.capturedRequest);
        assertEquals(10, client.capturedRequest.getRequestOptions().getTimeoutSeconds());
    }

    @Test
    void shouldReturnNullPayloadForNonJsonResponseFormat() {
        CapturingClient client = new CapturingClient();
        client.response = ChatResponseEnvelope.builder().providerType(AiProviderType.OPENAI).content("ok").build();

        AiProviderChatModel model = new AiProviderChatModel(
                new AiProviderClientRegistry(Map.of(AiProviderType.OPENAI, client)),
                objectMapper,
                "gpt-5.4",
                null,
                null,
                null,
                AiProviderType.OPENAI
        );

        AiChatRequest request = new AiChatRequest(
                "",
                List.of(AiChatMessage.user("hello")),
                List.of(),
                null,
                null,
                null,
                objectMapper.getNodeFactory().textNode("not-json")
        );

        var response = model.chat(request);
        assertNull(client.capturedRequest.getProviderPayload());
        assertTrue(response.message().toolCalls().isEmpty());
        assertNull(response.finishReason());
    }

    @Test
    void shouldUseResolvedRequestModelWhenProviderResponseDoesNotIncludeModel() {
        CapturingClient client = new CapturingClient();
        client.response = ChatResponseEnvelope.builder()
                .providerType(AiProviderType.OPENAI)
                .content("ok")
                .rawResponse(objectMapper.createObjectNode())
                .build();

        AiProviderChatModel model = new AiProviderChatModel(
                new AiProviderClientRegistry(Map.of(AiProviderType.OPENAI, client)),
                objectMapper,
                "gpt-5.4",
                null,
                null,
                null,
                AiProviderType.OPENAI
        );

        AiChatRequest request = new AiChatRequest(
                "",
                List.of(AiChatMessage.user("hello")),
                List.of(),
                null,
                null,
                null,
                null
        );

        var response = model.chat(request);

        assertEquals("gpt-5.4", client.capturedRequest.getModel());
        assertEquals("gpt-5.4", response.model());
    }

    @Test
    void shouldFailFastWhenProviderReturnsNullResponse() {
        CapturingClient client = new CapturingClient();

        AiProviderChatModel model = new AiProviderChatModel(
                new AiProviderClientRegistry(Map.of(AiProviderType.OPENAI, client)),
                objectMapper,
                "gpt-5.4",
                null,
                null,
                null,
                AiProviderType.OPENAI
        );

        AiChatRequest request = new AiChatRequest(
                "",
                List.of(AiChatMessage.user("hello")),
                List.of(),
                null,
                null,
                null,
                null
        );

        AiFrameworkException ex = assertThrows(AiFrameworkException.class, () -> model.chat(request));
        assertTrue(ex.getMessage().contains("empty response"));
    }

    private static class CapturingClient implements AiProviderClient {

        private ChatRequestEnvelope capturedRequest;
        private ChatResponseEnvelope response;

        @Override
        public ChatResponseEnvelope chat(ChatRequestEnvelope request) {
            this.capturedRequest = request;
            return response;
        }

        @Override
        public void streamChat(ChatRequestEnvelope request, StreamListener listener) {
            throw new UnsupportedOperationException("not used");
        }
    }
}
