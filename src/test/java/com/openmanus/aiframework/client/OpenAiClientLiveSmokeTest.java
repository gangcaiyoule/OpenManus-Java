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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("live-smoke")
@EnabledIfSystemProperty(
        named = LiveSmokeTest.LIVE_SMOKE_OPT_IN_PROPERTY,
        matches = LiveSmokeTest.LIVE_SMOKE_OPT_IN_PATTERN
)
class OpenAiClientLiveSmokeTest {

    private static final int LIVE_SMOKE_MAX_RETRIES = 3;
    private static final int LIVE_SMOKE_ATTEMPTS_PER_MODEL = 3;
    private static final long LIVE_SMOKE_RETRY_BACKOFF_MILLIS = 1_000L;

    @LiveSmokeTest
    void shouldCallLiveCompatibleOpenAiEndpointForChatAndStream() {
        LiveSmokeEnv.ProviderEnv env = LiveSmokeEnv.openAiCompatible();

        Assumptions.assumeTrue(env.isConfigured(),
                "live smoke test requires OPENMANUS_LIVE_MODEL[/CANDIDATES]/BASE_URL/API_KEY env vars "
                        + "or OPENMANUS_LLM_DEFAULT_LLM_MODEL/BASE_URL/API_KEY env vars "
                        + "or OPENAI_MODEL/BASE_URL/API_KEY env vars");

        ObjectMapper objectMapper = new ObjectMapper();
        List<String> attemptedModels = new ArrayList<>();
        List<String> failureMessages = new ArrayList<>();

        for (String candidateModel : env.candidateModels()) {
            attemptedModels.add(candidateModel);
            List<String> candidateFailures = new ArrayList<>();
            RuntimeException lastFailure = null;
            for (int attempt = 1; attempt <= LIVE_SMOKE_ATTEMPTS_PER_MODEL; attempt++) {
                try {
                    assertChatAndStreamSucceed(objectMapper, env, candidateModel);
                    return;
                } catch (RuntimeException e) {
                    lastFailure = e;
                    Assumptions.assumeFalse(shouldSkipForEnvironmentFailure(e),
                            "live smoke skipped because external gateway/TLS environment is not ready: "
                                    + summarizeFailure(e));
                    candidateFailures.add("attempt "
                            + attempt
                            + "/"
                            + LIVE_SMOKE_ATTEMPTS_PER_MODEL
                            + " -> "
                            + summarizeFailure(e));
                    if (!shouldRetryCandidateFailure(e) || attempt >= LIVE_SMOKE_ATTEMPTS_PER_MODEL) {
                        break;
                    }
                    if (attempt < LIVE_SMOKE_ATTEMPTS_PER_MODEL) {
                        sleepBeforeRetry();
                    }
                }
            }
            String candidateFailureSummary = summarizeAttemptFailures(candidateFailures);
            failureMessages.add(candidateModel + " -> " + candidateFailureSummary);
            if (lastFailure != null && shouldAbortRemainingCandidates(lastFailure)) {
                break;
            }
        }

        fail(buildAllCandidatesFailedMessage(attemptedModels, failureMessages));
    }

    static String buildAllCandidatesFailedMessage(List<String> attemptedModels, List<String> failureMessages) {
        Objects.requireNonNull(attemptedModels, "attemptedModels");
        Objects.requireNonNull(failureMessages, "failureMessages");

        StringJoiner detail = new StringJoiner("; ");
        for (String failureMessage : failureMessages) {
            detail.add(failureMessage);
        }
        return "all OpenAI-compatible live smoke model candidates failed: attempted="
                + attemptedModels + ", detail=" + detail;
    }

    static String summarizeAttemptFailures(List<String> failureMessages) {
        Objects.requireNonNull(failureMessages, "failureMessages");

        StringJoiner detail = new StringJoiner(" | ");
        for (String failureMessage : failureMessages) {
            detail.add(failureMessage);
        }
        return detail.toString();
    }

    static String summarizeFailure(Throwable error) {
        Objects.requireNonNull(error, "error");

        StringJoiner parts = new StringJoiner(" <- ");
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            String type = current.getClass().getSimpleName();
            parts.add(message == null || message.isBlank() ? type : message);
            current = current.getCause();
        }
        return parts.toString();
    }

    static boolean shouldRetryCandidateFailure(Throwable error) {
        Objects.requireNonNull(error, "error");

        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("code=model_not_found")
                        || normalized.contains("\"code\":\"model_not_found\"")
                        || normalized.contains("no available channel for model")
                        || normalized.contains("status=401")
                        || normalized.contains("status=402")
                        || normalized.contains("insufficient_quota")
                        || normalized.contains("insufficient_balance")
                        || normalized.contains("余额不足")
                        || normalized.contains("unauthorized")
                        || normalized.contains("invalid api key")
                        || normalized.contains("invalid authentication")
                        || normalized.contains("invalid token")
                        || normalized.contains("无效的令牌")) {
                    return false;
                }
            }
            current = current.getCause();
        }
        return true;
    }

    static boolean shouldAbortRemainingCandidates(Throwable error) {
        Objects.requireNonNull(error, "error");

        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("status=401")
                        || normalized.contains("status=402")
                        || normalized.contains("insufficient_quota")
                        || normalized.contains("insufficient_balance")
                        || normalized.contains("余额不足")
                        || normalized.contains("unauthorized")
                        || normalized.contains("invalid api key")
                        || normalized.contains("invalid authentication")
                        || normalized.contains("invalid token")
                        || normalized.contains("无效的令牌")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    static boolean shouldSkipForEnvironmentFailure(Throwable error) {
        Objects.requireNonNull(error, "error");

        Throwable current = error;
        while (current != null) {
            if (current instanceof SSLHandshakeException || current instanceof SSLException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("pkix path building failed")
                        || normalized.contains("unable to find valid certification path")
                        || normalized.contains("unable to find valid certification path to requested target")
                        || normalized.contains("sun.security.provider.certpath")
                        || normalized.contains("certificate_unknown")
                        || normalized.contains("remote host terminated the handshake")
                        || normalized.contains("unable to parse tls packet header")
                        || normalized.contains("handshake_failure")
                        || normalized.contains("handshake alert")
                        || normalized.contains("peer not authenticated")
                        || normalized.contains("status=402")
                        || normalized.contains("insufficient_quota")
                        || normalized.contains("insufficient_balance")
                        || normalized.contains("余额不足")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    static boolean hasMeaningfulResult(ChatResponseEnvelope response) {
        if (response == null) {
            return false;
        }
        if (response.getContent() != null && !response.getContent().isBlank()) {
            return true;
        }
        if (response.getToolCalls() != null && !response.getToolCalls().isEmpty()) {
            return true;
        }
        if (response.getFinishReason() != null && !response.getFinishReason().isBlank()) {
            return true;
        }
        return response.getUsage() != null || response.getRawResponse() != null;
    }

    private static void sleepBeforeRetry() {
        try {
            Thread.sleep(LIVE_SMOKE_RETRY_BACKOFF_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("live smoke retry backoff interrupted", e);
        }
    }

    private static void assertChatAndStreamSucceed(ObjectMapper objectMapper,
                                                   LiveSmokeEnv.ProviderEnv env,
                                                   String model) {
        OpenAiClient client = new OpenAiClient(
                ProviderConfig.builder()
                        .providerType(AiProviderType.OPENAI)
                        .baseUrl(env.baseUrl())
                        .apiKey(env.apiKey())
                        .model(model)
                        .timeoutSeconds(60)
                        .maxRetries(LIVE_SMOKE_MAX_RETRIES)
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
        assertTrue(hasMeaningfulResult(sync));

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

        if (error.get() != null) {
            throw new IllegalStateException(
                    "stream call failed for model " + model + ": " + summarizeFailure(error.get()),
                    error.get()
            );
        }
        assertNotNull(done.get());
        assertTrue(!deltas.isEmpty() || hasMeaningfulResult(done.get()));
        assertNotNull(deltas);
    }
}
