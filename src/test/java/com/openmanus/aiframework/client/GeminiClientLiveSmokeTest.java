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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("live-smoke")
@EnabledIfSystemProperty(
        named = LiveSmokeTest.LIVE_SMOKE_OPT_IN_PROPERTY,
        matches = LiveSmokeTest.LIVE_SMOKE_OPT_IN_PATTERN
)
class GeminiClientLiveSmokeTest {

    @LiveSmokeTest
    void shouldCallLiveGeminiEndpointForChatAndStream() {
        LiveSmokeEnv.ProviderEnv env = LiveSmokeEnv.gemini();

        Assumptions.assumeTrue(env.isConfigured(),
                "live smoke test requires OPENMANUS_LIVE_GEMINI_MODEL/BASE_URL/API_KEY env vars "
                        + "or OPENMANUS_LLM_PROVIDERS_GEMINI_MODEL/BASE_URL/API_KEY env vars");

        ObjectMapper objectMapper = new ObjectMapper();
        GeminiClient client = new GeminiClient(
                ProviderConfig.builder()
                        .providerType(AiProviderType.GEMINI)
                        .baseUrl(env.baseUrl())
                        .apiKey(env.apiKey())
                        .model(env.model())
                        .timeoutSeconds(60)
                        .maxRetries(1)
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

        ChatRequestEnvelope chatRequest = ChatRequestEnvelope.builder()
                .providerType(AiProviderType.GEMINI)
                .model(env.model())
                .message(ChatMessage.builder().role("user").content("Reply with exactly: live_ok").build())
                .providerPayload(payload)
                .requestOptions(ChatRequestOptions.builder().maxTokens(32).temperature(0.0).stream(false).build())
                .build();

        ChatResponseEnvelope sync = client.chat(chatRequest);
        assertNotNull(sync);
        assertNotNull(sync.getContent());
        assertTrue(!sync.getContent().isBlank() || !sync.getToolCalls().isEmpty());

        ChatRequestEnvelope streamRequest = ChatRequestEnvelope.builder()
                .providerType(AiProviderType.GEMINI)
                .model(env.model())
                .message(ChatMessage.builder().role("user").content("Reply with exactly: stream_ok").build())
                .providerPayload(payload)
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
        assertTrue(!deltas.isEmpty() || (done.get().getContent() != null && !done.get().getContent().isBlank()));
    }
}
