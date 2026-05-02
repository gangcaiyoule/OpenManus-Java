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
@DisplayName("真实 Agent Chat API E2E")
class AgentChatApiE2ETest extends RealApiE2ETestBase implements E2ETest {

    @Test
    @DisplayName("should execute real stateful chat flow and preserve memory across turns")
    void shouldExecuteRealStatefulChatFlowAndPreserveMemoryAcrossTurns() throws Exception {
        String conversationId = newConversationId("chat-e2e");
        String token = "MEM-" + newConversationId("token");

        JsonNode firstTurn = postJson(
                "/api/agent/chat?stateful=true&sync=true",
                """
                        {
                          "message": "请记住这个令牌：%s。请只回复：已记住 %s",
                          "conversationId": "%s"
                        }
                        """.formatted(token, token, conversationId)
        );

        assertThat(firstTurn.path("answer").asText()).isNotBlank().contains(token);
        assertThat(firstTurn.path("conversationId").asText()).isEqualTo(conversationId);
        assertThat(firstTurn.path("mode").asText()).isEqualTo("agent");

        JsonNode secondTurn = postJson(
                "/api/agent/chat?stateful=true&sync=true",
                """
                        {
                          "message": "请只回复刚才让你记住的令牌本身，不要添加任何其他字符。",
                          "conversationId": "%s"
                        }
                        """.formatted(conversationId)
        );

        assertThat(secondTurn.path("answer").asText()).isNotBlank().contains(token);
        assertThat(secondTurn.path("conversationId").asText()).isEqualTo(conversationId);
        assertThat(secondTurn.path("mode").asText()).isEqualTo("agent");
    }

    @Test
    @DisplayName("should reject invalid conversationId instead of downgrading the flow")
    void shouldRejectInvalidConversationId() {
        ResponseEntity<String> response = exchange(
                "/api/agent/chat?stateful=true&sync=true",
                """
                        {
                          "message": "hello",
                          "conversationId": "../bad"
                        }
                        """
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotBlank().contains("conversationId非法").contains("INPUT_INVALID");
    }
}
