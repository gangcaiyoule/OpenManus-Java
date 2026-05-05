package com.openmanus.e2e.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.openmanus.e2e.E2ETest;
import com.openmanus.e2e.support.RealApiE2ETestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("e2e")
@DisplayName("真实 Session API E2E")
class SessionApiE2ETest extends RealApiE2ETestBase implements E2ETest {

    @Test
    @DisplayName("should return session info for a real chat-created session")
    void shouldReturnSessionInfoForRealSession() throws Exception {
        String conversationId = newConversationId("session-e2e");

        JsonNode chatResponse = postJson(
                "/api/agent/chat?stateful=true&sync=true",
                """
                        {
                          "message": "请用一句话确认这个会话已经建立。",
                          "conversationId": "%s"
                        }
                        """.formatted(conversationId)
        );

        assertThat(chatResponse.path("answer").asText()).isNotBlank();
        assertThat(chatResponse.path("conversationId").asText()).isEqualTo(conversationId);

        JsonNode sessionResponse = getJson("/api/agent/session/" + conversationId);
        assertThat(sessionResponse.path("sessionId").asText()).isEqualTo(conversationId);
    }

    @Test
    @DisplayName("should reject invalid sessionId")
    void shouldRejectInvalidSessionId() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                url("/api/agent/session/bad.id"),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotBlank().contains("sessionId非法").contains("INPUT_INVALID");
    }
}
