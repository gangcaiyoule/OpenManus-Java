package com.openmanus.aiframework.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.aiframework.api.AiProviderClient;
import com.openmanus.aiframework.api.StreamListener;
import com.openmanus.aiframework.model.AiProviderType;
import com.openmanus.aiframework.model.ChatRequestEnvelope;
import com.openmanus.aiframework.model.ChatResponseEnvelope;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiFrameworkChatModelTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldConvertRequestAndResponseWithToolsAndSchema() throws Exception {
        CapturingClient client = new CapturingClient();
        client.response = ChatResponseEnvelope.builder()
                .providerType(AiProviderType.OPENAI)
                .content("")
                .toolCall(objectMapper.readTree("""
                        {
                          "id":"call_001",
                          "type":"function",
                          "function":{"name":"search","arguments":"{\\\"q\\\":\\\"today\\\"}"}
                        }
                        """))
                .finishReason("tool_calls")
                .usage(objectMapper.readTree("""
                        {"prompt_tokens":12,"completion_tokens":4,"total_tokens":16}
                        """))
                .rawResponse(objectMapper.readTree("""
                        {"id":"chatcmpl_1","model":"gpt-5.4"}
                        """))
                .build();

        OpenAiFrameworkChatModel model = new OpenAiFrameworkChatModel(
                client,
                objectMapper,
                "gpt-5.4",
                0.2,
                512,
                60
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
                .modelName("gpt-5.4")
                .temperature(0.1)
                .maxOutputTokens(256)
                .messages(
                        SystemMessage.from("you are helpful"),
                        UserMessage.from("weather?"),
                        AiMessage.from(ToolExecutionRequest.builder().id("call_001").name("search")
                                .arguments("{\"q\":\"today\"}").build()),
                        ToolExecutionResultMessage.from("call_001", "search", "sunny")
                )
                .toolSpecifications(List.of(toolSpec))
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormatType.JSON)
                        .jsonSchema(outputSchema)
                        .build())
                .build();

        ChatResponse response = model.chat(request);

        assertNotNull(client.capturedRequest);
        assertEquals("gpt-5.4", client.capturedRequest.getModel());
        assertEquals("assistant", client.capturedRequest.getMessages().get(2).getRole());
        assertTrue(client.capturedRequest.getMessages().get(2).getToolCalls().isArray());
        assertEquals("call_001", client.capturedRequest.getMessages().get(3).getToolCallId());
        assertEquals("search", client.capturedRequest.getProviderPayload().path("tools")
                .get(0).path("function").path("name").asText());
        assertEquals("json_schema", client.capturedRequest.getProviderPayload().path("response_format")
                .path("type").asText());

        assertNotNull(response.aiMessage());
        assertTrue(response.aiMessage().hasToolExecutionRequests());
        assertEquals("search", response.aiMessage().toolExecutionRequests().get(0).name());
        assertEquals(FinishReason.TOOL_EXECUTION, response.finishReason());
        assertEquals(16, response.tokenUsage().totalTokenCount());
        assertEquals("chatcmpl_1", response.id());
        assertEquals("gpt-5.4", response.modelName());
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
