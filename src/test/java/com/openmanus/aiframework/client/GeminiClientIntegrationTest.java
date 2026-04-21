package com.openmanus.aiframework.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openmanus.aiframework.api.StreamListener;
import com.openmanus.aiframework.assembler.GeminiRequestAssembler;
import com.openmanus.aiframework.model.AiProviderType;
import com.openmanus.aiframework.model.ChatMessage;
import com.openmanus.aiframework.model.ChatRequestEnvelope;
import com.openmanus.aiframework.model.ChatRequestOptions;
import com.openmanus.aiframework.model.ChatResponseEnvelope;
import com.openmanus.aiframework.model.ProviderConfig;
import com.openmanus.aiframework.parser.GeminiResponseParser;
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

class GeminiClientIntegrationTest {

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
        server.createContext("/v1beta/models/gemini-test:generateContent", exchange -> {
            String reqBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastRequestBody.set(reqBody);
            String body = """
                    {
                      "candidates": [{
                        "content": {
                          "parts": [
                            {"text":"Hello sync"},
                            {"functionCall":{"name":"search","args":{"q":"today"}}}
                          ]
                        },
                        "finishReason": "STOP"
                      }],
                      "usageMetadata": {
                        "promptTokenCount": 7,
                        "candidatesTokenCount": 5,
                        "totalTokenCount": 12
                      }
                    }
                    """;
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        server.createContext("/v1beta/models/gemini-test:streamGenerateContent", exchange -> {
            String reqBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastRequestBody.set(reqBody);
            String body = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hel\"},{\"functionCall\":{\"name\":\"search\",\"args\":{\"q\":\"today\"}}}]}}]}\n\n"
                    + "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"lo\"}]},\"finishReason\":\"STOP\"}],\"usageMetadata\":{\"promptTokenCount\":9,\"candidatesTokenCount\":2,\"totalTokenCount\":11}}\n\n";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        ObjectMapper objectMapper = new ObjectMapper();
        GeminiClient client = new GeminiClient(
                ProviderConfig.builder()
                        .providerType(AiProviderType.GEMINI)
                        .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                        .apiKey("test-key")
                        .model("gemini-test")
                        .timeoutSeconds(5)
                        .maxRetries(0)
                        .build(),
                new GeminiRequestAssembler(objectMapper),
                new GeminiResponseParser(),
                new HttpTransport(HttpClient.newHttpClient(), objectMapper),
                new SseTransport(HttpClient.newHttpClient(), objectMapper),
                objectMapper
        );

        ObjectNode payload = objectMapper.createObjectNode();
        payload.putArray("tools")
                .addObject()
                .putArray("functionDeclarations")
                .addObject()
                .put("name", "search")
                .put("description", "Search web")
                .putObject("parameters")
                .put("type", "object")
                .putObject("properties")
                .putObject("q")
                .put("type", "string");
        payload.putObject("generationConfig")
                .put("responseMimeType", "application/json")
                .putObject("responseSchema")
                .put("type", "object")
                .putObject("properties")
                .putObject("answer")
                .put("type", "string");

        ChatRequestEnvelope request = ChatRequestEnvelope.builder()
                .providerType(AiProviderType.GEMINI)
                .model("gemini-test")
                .message(ChatMessage.builder().role("user").content("hello").build())
                .providerPayload(payload)
                .requestOptions(ChatRequestOptions.builder().stream(false).build())
                .build();

        ChatResponseEnvelope sync = client.chat(request);
        assertEquals("Hello sync", sync.getContent());
        assertEquals("STOP", sync.getFinishReason());
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
        assertEquals("STOP", done.get().getFinishReason());
        assertEquals(1, done.get().getToolCalls().size());
        assertTrue(lastRequestBody.get().contains("\"functionDeclarations\""));
        assertTrue(lastRequestBody.get().contains("\"responseSchema\""));
    }
}
