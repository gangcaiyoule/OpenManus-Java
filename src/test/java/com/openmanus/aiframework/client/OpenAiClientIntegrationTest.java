package com.openmanus.aiframework.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.aiframework.api.StreamListener;
import com.openmanus.aiframework.assembler.OpenAiRequestAssembler;
import com.openmanus.aiframework.model.AiProviderType;
import com.openmanus.aiframework.model.ChatMessage;
import com.openmanus.aiframework.model.ChatRequestEnvelope;
import com.openmanus.aiframework.model.ChatRequestOptions;
import com.openmanus.aiframework.model.ChatResponseEnvelope;
import com.openmanus.aiframework.model.ProviderConfig;
import com.openmanus.aiframework.parser.OpenAiResponseParser;
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

class OpenAiClientIntegrationTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldHandleChatAndStream() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            String reqBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean stream = reqBody.contains("\"stream\":true");
            String body;
            if (stream) {
                body = "data: {\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}\n\n"
                        + "data: {\"choices\":[{\"delta\":{\"content\":\"lo\"},\"finish_reason\":\"stop\"}]}\n\n"
                        + "data: [DONE]\n\n";
                exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            } else {
                body = """
                        {
                          "choices": [{"message": {"content": "Hello sync"}, "finish_reason": "stop"}],
                          "usage": {"prompt_tokens": 8, "completion_tokens": 2}
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
        OpenAiClient client = new OpenAiClient(
                ProviderConfig.builder()
                        .providerType(AiProviderType.OPENAI)
                        .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                        .apiKey("test-key")
                        .model("gpt-4o-mini")
                        .timeoutSeconds(5)
                        .maxRetries(0)
                        .build(),
                new OpenAiRequestAssembler(objectMapper),
                new OpenAiResponseParser(),
                new HttpTransport(HttpClient.newHttpClient(), objectMapper),
                new SseTransport(HttpClient.newHttpClient(), objectMapper),
                objectMapper
        );

        ChatRequestEnvelope request = ChatRequestEnvelope.builder()
                .providerType(AiProviderType.OPENAI)
                .model("gpt-4o-mini")
                .message(ChatMessage.builder().role("user").content("hello").build())
                .requestOptions(ChatRequestOptions.builder().stream(false).build())
                .build();

        ChatResponseEnvelope sync = client.chat(request);
        assertEquals("Hello sync", sync.getContent());
        assertEquals("stop", sync.getFinishReason());

        List<String> deltas = new ArrayList<>();
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
        assertEquals("Hello", done.get().getContent());
        assertEquals("stop", done.get().getFinishReason());
    }
}
