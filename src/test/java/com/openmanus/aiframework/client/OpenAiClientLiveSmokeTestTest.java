package com.openmanus.aiframework.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.openmanus.aiframework.model.ChatResponseEnvelope;
import com.openmanus.aiframework.model.AiProviderType;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiClientLiveSmokeTestTest {

    @Test
    void shouldBuildFailureMessageForAllCandidateFailures() {
        String message = OpenAiClientLiveSmokeTest.buildAllCandidatesFailedMessage(
                List.of("gpt-5.4", "gpt-5-mini"),
                List.of("gpt-5.4 -> 403 bad_response_status_code", "gpt-5-mini -> 503 model_not_found"));

        assertEquals(
                "all OpenAI-compatible live smoke model candidates failed: attempted=[gpt-5.4, gpt-5-mini], "
                        + "detail=gpt-5.4 -> 403 bad_response_status_code; gpt-5-mini -> 503 model_not_found",
                message);
    }

    @Test
    void shouldAllowEmptyFailureDetailsWhenNoFailureMessagesWereCollected() {
        String message = OpenAiClientLiveSmokeTest.buildAllCandidatesFailedMessage(
                List.of("gpt-5.4"),
                List.of());

        assertEquals("all OpenAI-compatible live smoke model candidates failed: attempted=[gpt-5.4], detail=",
                message);
    }

    @Test
    void shouldRejectNullInputsWhenBuildingFailureMessage() {
        assertThrows(NullPointerException.class,
                () -> OpenAiClientLiveSmokeTest.buildAllCandidatesFailedMessage(null, List.of()));
        assertThrows(NullPointerException.class,
                () -> OpenAiClientLiveSmokeTest.buildAllCandidatesFailedMessage(List.of("gpt-5.4"), null));
    }

    @Test
    void shouldSummarizeAttemptFailuresInOrder() {
        String message = OpenAiClientLiveSmokeTest.summarizeAttemptFailures(
                List.of("attempt 1/3 -> 503 service_unavailable", "attempt 2/3 -> 530 bad_response_status_code"));

        assertEquals("attempt 1/3 -> 503 service_unavailable | attempt 2/3 -> 530 bad_response_status_code",
                message);
    }

    @Test
    void shouldRejectNullAttemptFailures() {
        assertThrows(NullPointerException.class, () -> OpenAiClientLiveSmokeTest.summarizeAttemptFailures(null));
    }

    @Test
    void shouldSummarizeNestedFailureMessages() {
        String message = OpenAiClientLiveSmokeTest.summarizeFailure(
                new IllegalStateException("stream call failed for model gpt-5.4",
                        new RuntimeException("SSE provider request failed: status=503, body={\"error\":\"upstream\"}")));

        assertEquals("stream call failed for model gpt-5.4 <- SSE provider request failed: status=503, body={\"error\":\"upstream\"}",
                message);
    }

    @Test
    void shouldFallbackToThrowableTypeWhenFailureMessageIsBlank() {
        String message = OpenAiClientLiveSmokeTest.summarizeFailure(new RuntimeException(""));

        assertEquals("RuntimeException", message);
    }

    @Test
    void shouldStopRetryingWhenFailureIsModelNotFound() {
        assertFalse(OpenAiClientLiveSmokeTest.shouldRetryCandidateFailure(
                new IllegalStateException("Provider request failed: status=503, body={\"error\":{\"code\":\"model_not_found\"}}")));
        assertFalse(OpenAiClientLiveSmokeTest.shouldRetryCandidateFailure(
                new IllegalStateException("stream call failed",
                        new RuntimeException("No available channel for model gpt-5.4 under group default"))));
        assertFalse(OpenAiClientLiveSmokeTest.shouldRetryCandidateFailure(
                new IllegalStateException("Provider request failed: status=401, body={\"error\":{\"message\":\"无效的令牌\"}}")));
        assertFalse(OpenAiClientLiveSmokeTest.shouldRetryCandidateFailure(
                new IllegalStateException("stream call failed",
                        new RuntimeException("Unauthorized: invalid api key"))));
        assertFalse(OpenAiClientLiveSmokeTest.shouldRetryCandidateFailure(
                new IllegalStateException("Provider request failed: status=402, body={\"error\":{\"type\":\"insufficient_quota\",\"code\":\"insufficient_balance\"}}")));
        assertFalse(OpenAiClientLiveSmokeTest.shouldRetryCandidateFailure(
                new IllegalStateException("stream call failed",
                        new RuntimeException("余额不足，请先充值"))));
    }

    @Test
    void shouldKeepRetryingWhenFailureLooksTransient() {
        assertTrue(OpenAiClientLiveSmokeTest.shouldRetryCandidateFailure(
                new IllegalStateException("Provider returned empty SSE response without content or finish reason")));
        assertTrue(OpenAiClientLiveSmokeTest.shouldRetryCandidateFailure(
                new IllegalStateException("stream call failed",
                        new RuntimeException("SSE provider request failed: status=403, body={\"error\":{\"code\":\"bad_response_status_code\"}}"))));
    }

    @Test
    void shouldAbortRemainingCandidatesWhenFailureIsClearlyNotModelSpecific() {
        assertTrue(OpenAiClientLiveSmokeTest.shouldAbortRemainingCandidates(
                new IllegalStateException("Provider request failed: status=401, body={\"error\":{\"message\":\"invalid api key\"}}")));
        assertTrue(OpenAiClientLiveSmokeTest.shouldAbortRemainingCandidates(
                new IllegalStateException("Provider request failed: status=402, body={\"error\":{\"type\":\"insufficient_quota\",\"code\":\"insufficient_balance\"}}")));
        assertTrue(OpenAiClientLiveSmokeTest.shouldAbortRemainingCandidates(
                new IllegalStateException("stream call failed",
                        new RuntimeException("Unauthorized: invalid token"))));
    }

    @Test
    void shouldContinueOtherCandidatesWhenFailureLooksModelSpecific() {
        assertFalse(OpenAiClientLiveSmokeTest.shouldAbortRemainingCandidates(
                new IllegalStateException("Provider request failed: status=404, body={\"error\":{\"code\":\"model_not_found\"}}")));
        assertFalse(OpenAiClientLiveSmokeTest.shouldAbortRemainingCandidates(
                new IllegalStateException("stream call failed",
                        new RuntimeException("No available channel for model gpt-5.4 under group default"))));
        assertFalse(OpenAiClientLiveSmokeTest.shouldAbortRemainingCandidates(
                new IllegalStateException("Provider request failed: status=503, body={\"error\":{\"code\":\"upstream_timeout\"}}")));
    }

    @Test
    void shouldRejectNullFailureWhenCheckingAbortRemainingCandidates() {
        assertThrows(NullPointerException.class,
                () -> OpenAiClientLiveSmokeTest.shouldAbortRemainingCandidates(null));
    }

    @Test
    void shouldRejectNullFailureWhenCheckingRetryability() {
        assertThrows(NullPointerException.class, () -> OpenAiClientLiveSmokeTest.shouldRetryCandidateFailure(null));
    }

    @Test
    void shouldSkipWhenFailureLooksLikeTlsEnvironmentProblem() {
        assertTrue(OpenAiClientLiveSmokeTest.shouldSkipForEnvironmentFailure(
                new IllegalStateException("stream call failed",
                        new SSLHandshakeException("PKIX path building failed"))));
        assertTrue(OpenAiClientLiveSmokeTest.shouldSkipForEnvironmentFailure(
                new IllegalStateException("Provider request failed",
                        new RuntimeException("unable to find valid certification path to requested target"))));
        assertTrue(OpenAiClientLiveSmokeTest.shouldSkipForEnvironmentFailure(
                new IllegalStateException("stream call failed",
                        new RuntimeException("Remote host terminated the handshake"))));
        assertTrue(OpenAiClientLiveSmokeTest.shouldSkipForEnvironmentFailure(
                new IllegalStateException("stream call failed",
                        new RuntimeException("received handshake alert: unrecognized_name"))));
    }

    @Test
    void shouldSkipWhenFailureContainsOtherTlsTrustOrProtocolSignals() {
        assertTrue(OpenAiClientLiveSmokeTest.shouldSkipForEnvironmentFailure(
                new IllegalStateException("stream call failed",
                        new RuntimeException("certificate_unknown"))));
        assertTrue(OpenAiClientLiveSmokeTest.shouldSkipForEnvironmentFailure(
                new IllegalStateException("Provider request failed",
                        new RuntimeException("peer not authenticated"))));
        assertTrue(OpenAiClientLiveSmokeTest.shouldSkipForEnvironmentFailure(
                new IllegalStateException("stream call failed",
                        new RuntimeException("unable to parse TLS packet header"))));
    }

    @Test
    void shouldSkipWhenFailureShowsExternalQuotaOrBalanceBlocker() {
        assertTrue(OpenAiClientLiveSmokeTest.shouldSkipForEnvironmentFailure(
                new IllegalStateException("Provider request failed: status=402, body={\"error\":{\"type\":\"insufficient_quota\",\"code\":\"insufficient_balance\"}}")));
        assertTrue(OpenAiClientLiveSmokeTest.shouldSkipForEnvironmentFailure(
                new IllegalStateException("stream call failed",
                        new RuntimeException("余额不足，请先充值"))));
    }

    @Test
    void shouldNotSkipWhenFailureIsBusinessOrCredentialError() {
        assertFalse(OpenAiClientLiveSmokeTest.shouldSkipForEnvironmentFailure(
                new IllegalStateException("Provider request failed: status=401, body={\"error\":\"unauthorized\"}")));
        assertFalse(OpenAiClientLiveSmokeTest.shouldSkipForEnvironmentFailure(
                new IllegalStateException("all OpenAI-compatible live smoke model candidates failed")));
    }

    @Test
    void shouldRejectNullFailureWhenCheckingEnvironmentSkip() {
        assertThrows(NullPointerException.class, () -> OpenAiClientLiveSmokeTest.shouldSkipForEnvironmentFailure(null));
    }

    @Test
    void shouldTreatNonBlankContentAsMeaningfulLiveSmokeResult() {
        ChatResponseEnvelope response = ChatResponseEnvelope.builder()
                .providerType(AiProviderType.OPENAI)
                .content("live_ok")
                .build();

        assertTrue(OpenAiClientLiveSmokeTest.hasMeaningfulResult(response));
    }

    @Test
    void shouldTreatUsageOrRawResponseAsMeaningfulLiveSmokeResultWhenContentIsBlank() {
        ObjectMapper objectMapper = new ObjectMapper();
        ChatResponseEnvelope response = ChatResponseEnvelope.builder()
                .providerType(AiProviderType.OPENAI)
                .content(" ")
                .finishReason("stop")
                .rawResponse(objectMapper.createObjectNode().put("id", "resp_123"))
                .build();

        assertTrue(OpenAiClientLiveSmokeTest.hasMeaningfulResult(response));
    }

    @Test
    void shouldTreatToolCallsAsMeaningfulLiveSmokeResultWhenContentIsBlank() {
        ChatResponseEnvelope response = ChatResponseEnvelope.builder()
                .providerType(AiProviderType.OPENAI)
                .content(" ")
                .toolCalls(List.of(JsonNodeFactory.instance.objectNode().put("name", "browser_search")))
                .build();

        assertTrue(OpenAiClientLiveSmokeTest.hasMeaningfulResult(response));
    }

    @Test
    void shouldRejectCompletelyEmptyLiveSmokeResult() {
        ChatResponseEnvelope response = ChatResponseEnvelope.builder()
                .providerType(AiProviderType.OPENAI)
                .content(" ")
                .build();

        assertFalse(OpenAiClientLiveSmokeTest.hasMeaningfulResult(response));
        assertFalse(OpenAiClientLiveSmokeTest.hasMeaningfulResult(null));
    }
}
