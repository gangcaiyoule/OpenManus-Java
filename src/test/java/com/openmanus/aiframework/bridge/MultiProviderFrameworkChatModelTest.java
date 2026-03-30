package com.openmanus.aiframework.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.aiframework.api.AiProviderClient;
import com.openmanus.aiframework.api.StreamListener;
import com.openmanus.aiframework.config.AiProviderClientRegistry;
import com.openmanus.aiframework.model.AiProviderType;
import com.openmanus.aiframework.model.ChatRequestEnvelope;
import com.openmanus.aiframework.model.ChatResponseEnvelope;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiProviderFrameworkChatModelTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldMapToolAndSchemaForAnthropic() throws Exception {
        CapturingClient client = new CapturingClient();
        client.response = ChatResponseEnvelope.builder()
                .providerType(AiProviderType.ANTHROPIC)
                .content("")
                .toolCall(objectMapper.readTree("""
                        {
                          "id":"toolu_1",
                          "type":"tool_use",
                          "name":"search",
                          "input":{"q":"today"}
                        }
                        """))
                .finishReason("tool_use")
                .usage(objectMapper.readTree("""
                        {"input_tokens":11,"output_tokens":5}
                        """))
                .rawResponse(objectMapper.readTree("""
                        {"id":"msg_1","model":"claude-3-5-sonnet-latest"}
                        """))
                .build();

        OpenAiFrameworkChatModel model = new OpenAiFrameworkChatModel(
                new AiProviderClientRegistry(Map.of(AiProviderType.ANTHROPIC, client)),
                objectMapper,
                "claude-3-5-sonnet-latest",
                0.2,
                512,
                60,
                AiProviderType.ANTHROPIC
        );

        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("search")
                .description("Search web")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("q", "query")
                        .required("q")
                        .additionalProperties(false)
                        .build())
                .build();

        JsonSchema outputSchema = JsonSchema.builder()
                .name("final_answer")
                .rootElement(JsonObjectSchema.builder()
                        .addStringProperty("answer")
                        .required("answer")
                        .build())
                .build();

        ChatRequest request = ChatRequest.builder()
                .modelName("claude-3-5-sonnet-latest")
                .messages(dev.langchain4j.data.message.UserMessage.from("weather?"))
                .toolSpecifications(List.of(toolSpec))
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormatType.JSON)
                        .jsonSchema(outputSchema)
                        .build())
                .build();

        ChatResponse response = model.chat(request);

        assertNotNull(client.capturedRequest);
        assertEquals(AiProviderType.ANTHROPIC, client.capturedRequest.getProviderType());
        assertEquals("search", client.capturedRequest.getProviderPayload().path("tools").get(0).path("name").asText());
        assertEquals("structured_output",
                client.capturedRequest.getProviderPayload().path("tool_choice").path("name").asText());

        assertTrue(response.aiMessage().hasToolExecutionRequests());
        assertEquals("search", response.aiMessage().toolExecutionRequests().get(0).name());
        assertTrue(response.aiMessage().toolExecutionRequests().get(0).arguments().contains("\"q\":\"today\""));
        assertEquals(FinishReason.TOOL_EXECUTION, response.finishReason());
    }

    @Test
    void shouldMapToolAndSchemaForGemini() throws Exception {
        CapturingClient client = new CapturingClient();
        client.response = ChatResponseEnvelope.builder()
                .providerType(AiProviderType.GEMINI)
                .content("ok")
                .toolCall(objectMapper.readTree("""
                        {
                          "functionCall": {
                            "name": "search",
                            "args": {"q":"today"}
                          }
                        }
                        """))
                .finishReason("STOP")
                .usage(objectMapper.readTree("""
                        {"promptTokenCount":10,"candidatesTokenCount":3,"totalTokenCount":13}
                        """))
                .rawResponse(objectMapper.readTree("""
                        {"id":"resp_1","modelVersion":"gemini-2.5-flash"}
                        """))
                .build();

        OpenAiFrameworkChatModel model = new OpenAiFrameworkChatModel(
                new AiProviderClientRegistry(Map.of(AiProviderType.GEMINI, client)),
                objectMapper,
                "gemini-2.5-flash",
                0.2,
                512,
                60,
                AiProviderType.GEMINI
        );

        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("search")
                .description("Search web")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("q", "query")
                        .required("q")
                        .additionalProperties(false)
                        .build())
                .build();

        JsonSchema outputSchema = JsonSchema.builder()
                .name("final_answer")
                .rootElement(JsonObjectSchema.builder()
                        .addStringProperty("answer")
                        .required("answer")
                        .build())
                .build();

        ChatRequest request = ChatRequest.builder()
                .modelName("gemini-2.5-flash")
                .messages(dev.langchain4j.data.message.UserMessage.from("weather?"))
                .toolSpecifications(List.of(toolSpec))
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormatType.JSON)
                        .jsonSchema(outputSchema)
                        .build())
                .build();

        ChatResponse response = model.chat(request);

        assertNotNull(client.capturedRequest);
        assertEquals(AiProviderType.GEMINI, client.capturedRequest.getProviderType());
        assertEquals("search",
                client.capturedRequest.getProviderPayload().path("tools")
                        .get(0).path("functionDeclarations").get(0).path("name").asText());
        assertEquals("application/json",
                client.capturedRequest.getProviderPayload().path("generationConfig")
                        .path("responseMimeType").asText());

        assertTrue(response.aiMessage().hasToolExecutionRequests());
        assertEquals("search", response.aiMessage().toolExecutionRequests().get(0).name());
        assertTrue(response.aiMessage().toolExecutionRequests().get(0).arguments().contains("\"q\":\"today\""));
        assertEquals(FinishReason.STOP, response.finishReason());
        assertNotNull(response.tokenUsage());
        assertEquals(13, response.tokenUsage().totalTokenCount());
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
