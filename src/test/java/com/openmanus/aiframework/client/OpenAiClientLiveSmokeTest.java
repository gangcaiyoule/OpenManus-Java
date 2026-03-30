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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OpenAiClientLiveSmokeTest {

    @Test
    void shouldCallLiveCompatibleOpenAiEndpointForChatAndStream() {
        String model = System.getenv("OPENMANUS_LIVE_MODEL");
        String baseUrl = System.getenv("OPENMANUS_LIVE_BASE_URL");
        String apiKey = System.getenv("OPENMANUS_LIVE_API_KEY");

        Assumptions.assumeTrue(notBlank(model) && notBlank(baseUrl) && notBlank(apiKey),
                "live smoke test requires OPENMANUS_LIVE_MODEL/BASE_URL/API_KEY env vars");

        ObjectMapper objectMapper = new ObjectMapper();
        OpenAiClient client = new OpenAiClient(
                ProviderConfig.builder()
                        .providerType(AiProviderType.OPENAI)
                        .baseUrl(baseUrl)
                        .apiKey(apiKey)
                        .model(model)
                        .timeoutSeconds(60)
                        .maxRetries(1)
                        .build(),
                new OpenAiRequestAssembler(objectMapper),
                new OpenAiResponseParser(),
                new HttpTransport(HttpClient.newHttpClient(), objectMapper),
                new SseTransport(HttpClient.newHttpClient(), objectMapper),
                objectMapper
        );

        ChatRequestEnvelope chatRequest = ChatRequestEnvelope.builder()
                .providerType(AiProviderType.OPENAI)
                .model(model)
                .message(ChatMessage.builder().role("user").content("Reply with exactly: live_ok").build())
                .requestOptions(ChatRequestOptions.builder().maxTokens(32).temperature(0.0).stream(false).build())
                .build();

        ChatResponseEnvelope sync = client.chat(chatRequest);
        assertNotNull(sync);
        assertNotNull(sync.getContent());
        assertFalse(sync.getContent().isBlank());

        ChatRequestEnvelope streamRequest = ChatRequestEnvelope.builder()
                .providerType(AiProviderType.OPENAI)
                .model(model)
                .message(ChatMessage.builder().role("user").content("Reply with exactly: stream_ok").build())
                .requestOptions(ChatRequestOptions.builder().maxTokens(32).temperature(0.0).stream(true).build())
                .build();

        List<String> deltas = new ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<ChatResponseEnvelope> done = new AtomicReference<>();

        client.streamChat(streamRequest, new StreamListener() {
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

        Assumptions.assumeTrue(error.get() == null, "stream call failed: " + error.get());
        assertNotNull(done.get());
        assertNotNull(done.get().getContent());
        assertFalse(done.get().getContent().isBlank());
        assertNotNull(deltas);
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
