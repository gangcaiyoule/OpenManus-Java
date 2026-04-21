package com.openmanus.aiframework.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openmanus.aiframework.api.StreamListener;
import com.openmanus.aiframework.assembler.AnthropicRequestAssembler;
import com.openmanus.aiframework.model.AiProviderType;
import com.openmanus.aiframework.model.ChatMessage;
import com.openmanus.aiframework.model.ChatRequestEnvelope;
import com.openmanus.aiframework.model.ChatRequestOptions;
import com.openmanus.aiframework.model.ChatResponseEnvelope;
import com.openmanus.aiframework.model.ProviderConfig;
import com.openmanus.aiframework.parser.AnthropicResponseParser;
import com.openmanus.aiframework.transport.HttpTransport;
import com.openmanus.aiframework.transport.SseTransport;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnthropicClientIntegrationTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldHandleChatAndStreamWithToolsAndSchemaPayload() throws Exception {
        AtomicReference<String> lastRequestBody = new AtomicReference<>();

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/messages", exchange -> {
            String reqBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastRequestBody.set(reqBody);
            boolean stream = reqBody.contains("\"stream\":true");
            String body;
            if (stream) {
                body = "event: content_block_delta\n"
                        + "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hel\"}}\n\n"
                        + "event: content_block_start\n"
                        + "data: {\"type\":\"content_block_start\",\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"search\",\"input\":{\"q\":\"today\"}}}\n\n"
                        + "event: content_block_delta\n"
                        + "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"lo\"}}\n\n"
                        + "event: message_delta\n"
                        + "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"input_tokens\":9,\"output_tokens\":2}}\n\n"
                        + "event: message_stop\n"
                        + "data: {\"type\":\"message_stop\"}\n\n";
                exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            } else {
                body = """
                        {
                          "content": [
                            {"type":"text","text":"Hello sync"},
                            {"type":"tool_use","id":"toolu_sync","name":"search","input":{"q":"today"}}
                          ],
                          "stop_reason": "tool_use",
                          "usage": {"input_tokens": 8, "output_tokens": 3}
                        }
                        """;
                exchange.getResponseHeaders().add("Content-Type", "application/json");
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        ObjectMapper objectMapper = new ObjectMapper();
        AnthropicClient client = new AnthropicClient(
                ProviderConfig.builder()
                        .providerType(AiProviderType.ANTHROPIC)
                        .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                        .apiKey("test-key")
                        .model("claude-3-5-sonnet-latest")
                        .timeoutSeconds(5)
                        .maxRetries(0)
                        .build(),
                new AnthropicRequestAssembler(objectMapper),
                new AnthropicResponseParser(),
                new HttpTransport(HttpClient.newHttpClient(), objectMapper),
                new SseTransport(HttpClient.newHttpClient(), objectMapper),
                objectMapper
        );

        ObjectNode payload = objectMapper.createObjectNode();
        payload.putArray("tools")
                .addObject()
                .put("name", "search")
                .put("description", "Search web")
                .putObject("input_schema")
                .put("type", "object")
                .putObject("properties")
                .putObject("q")
                .put("type", "string");
        payload.putObject("tool_choice")
                .put("type", "tool")
                .put("name", "search");

        ChatRequestEnvelope request = ChatRequestEnvelope.builder()
                .providerType(AiProviderType.ANTHROPIC)
                .model("claude-3-5-sonnet-latest")
                .message(ChatMessage.builder().role("user").content("hello").build())
                .providerPayload(payload)
                .requestOptions(ChatRequestOptions.builder().stream(false).build())
                .build();

        ChatResponseEnvelope sync = client.chat(request);
        assertEquals("Hello sync", sync.getContent());
        assertEquals("tool_use", sync.getFinishReason());
        assertEquals(1, sync.getToolCalls().size());
        assertEquals("search", sync.getToolCalls().get(0).path("name").asText());

        List<String> deltas = new ArrayList<>();
        List<String> streamToolCalls = new ArrayList<>();
        AtomicReference<ChatResponseEnvelope> done = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        ChatRequestEnvelope streamRequest = ChatRequestEnvelope.builder()
                .providerType(request.getProviderType())
                .model(request.getModel())
                .messages(request.getMessages())
                .providerPayload(request.getProviderPayload())
                .requestOptions(ChatRequestOptions.builder().stream(true).build())
                .build();

        client.streamChat(streamRequest,
                new StreamListener() {
                    @Override
                    public void onDelta(String deltaText) {
                        deltas.add(deltaText);
                    }

                    @Override
                    public void onToolCall(String providerRawToolCallJson) {
                        streamToolCalls.add(providerRawToolCallJson);
                    }

                    @Override
                    public void onComplete(ChatResponseEnvelope finalResponse) {
                        done.set(finalResponse);
                    }

                    @Override
                    public void onError(Throwable e) {
                        error.set(e);
                    }
                });

        assertNull(error.get());
        assertEquals(List.of("Hel", "lo"), deltas);
        assertTrue(streamToolCalls.stream().anyMatch(v -> v.contains("\"name\":\"search\"")));
        assertEquals("Hello", done.get().getContent());
        assertEquals("end_turn", done.get().getFinishReason());
        assertEquals(1, done.get().getToolCalls().size());
        assertTrue(lastRequestBody.get().contains("\"tools\""));
        assertTrue(lastRequestBody.get().contains("\"tool_choice\""));
    }
}
