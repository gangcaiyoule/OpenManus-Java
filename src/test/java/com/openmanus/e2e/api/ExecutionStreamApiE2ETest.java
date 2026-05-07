package com.openmanus.e2e.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.openmanus.domain.model.AgentExecutionEvent;
import com.openmanus.e2e.E2ETest;
import com.openmanus.e2e.support.RealApiE2ETestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("e2e")
@DisplayName("真实 Execution Stream API E2E")
class ExecutionStreamApiE2ETest extends RealApiE2ETestBase implements E2ETest {

    @Test
    @DisplayName("should execute real execution stream and expose terminal events")
    void shouldExecuteRealExecutionStreamAndExposeTerminalEvents() throws Exception {
        String marker = "STREAM-" + newConversationId("marker");

        JsonNode startResponse = postJson(
                "/api/agent/execution-stream",
                """
                        {
                          "input": "请原样包含这个标记：%s，并用一句简短中文回复你已收到。"
                        }
                        """.formatted(marker)
        );

        assertThat(startResponse.path("success").asBoolean()).isTrue();
        String sessionId = startResponse.path("sessionId").asText();
        String executionId = startResponse.path("executionId").asText();
        assertThat(sessionId).isNotBlank();
        assertThat(executionId).isNotBlank();
        assertThat(startResponse.path("topic").asText())
                .isEqualTo("/topic/executions/" + sessionId + "/" + executionId);

        List<AgentExecutionEvent> events = waitForSessionEvents(sessionId, hasTerminalExecutionEvent());

        assertThat(events)
                .extracting(AgentExecutionEvent::getEventType)
                .contains(AgentExecutionEvent.EventType.AGENT_START, AgentExecutionEvent.EventType.AGENT_END);

        AgentExecutionEvent terminalEvent = events.stream()
                .filter(event -> event.getEventType() == AgentExecutionEvent.EventType.AGENT_END)
                .reduce((first, second) -> second)
                .orElseThrow();

        assertThat(String.valueOf(terminalEvent.getOutput())).isNotBlank().contains(marker);
        assertThat(terminalEvent.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("should reject blank execution input")
    void shouldRejectBlankExecutionInput() {
        ResponseEntity<String> response = exchange(
                "/api/agent/execution-stream",
                """
                        {
                          "input": "   "
                        }
                        """
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotBlank().contains("输入不能为空").contains("INPUT_INVALID");
    }
}
