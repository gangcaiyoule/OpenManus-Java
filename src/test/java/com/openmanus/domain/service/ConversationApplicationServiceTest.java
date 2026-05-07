package com.openmanus.domain.service;

import com.openmanus.domain.model.ExecutionErrorCodes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("ConversationApplicationService Tests")
class ConversationApplicationServiceTest {

    private final AgentExecutionPort agentExecutionPort = mock(AgentExecutionPort.class);
    private final ExecutionEventPort executionEventPort = mock(ExecutionEventPort.class);
    private final SessionExecutionGuard guard = new SessionExecutionGuard() {
        @Override
        public boolean tryAcquire(String sessionId) {
            return false;
        }

        @Override
        public void release(String sessionId) {
        }
    };

    @Test
    @DisplayName("should reject chat request when session is busy")
    void shouldRejectChatRequestWhenSessionIsBusy() {
        ConversationApplicationService service = new ConversationApplicationService(
                agentExecutionPort,
                executionEventPort,
                guard
        );

        CompletableFuture<Map<String, Object>> future = service.chat("hello", "session-1", true);
        Map<String, Object> result = future.join();

        assertThat(result.get("conversationId")).isEqualTo("session-1");
        assertThat(result.get("errorCode")).isEqualTo(ExecutionErrorCodes.SESSION_BUSY);
        assertThat(String.valueOf(result.get("error"))).contains("当前会话正在执行中");
    }
}
