package com.openmanus.domain.service;

import com.openmanus.domain.model.ExecutionErrorCodes;
import com.openmanus.domain.model.ExecutionResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ExecutionStreamingApplicationService Tests")
class ExecutionStreamingApplicationServiceTest {

    private final AgentExecutionPort agentExecutionPort = mock(AgentExecutionPort.class);
    private final ExecutionEventPort executionEventPort = mock(ExecutionEventPort.class);
    private final ExecutionStreamPublisher executionStreamPublisher = mock(ExecutionStreamPublisher.class);
    private final Executor executor = Runnable::run;
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
    @DisplayName("should reject streaming request when session is busy")
    void shouldRejectStreamingRequestWhenSessionIsBusy() {
        ExecutionStreamingApplicationService service = new ExecutionStreamingApplicationService(
                agentExecutionPort,
                executionEventPort,
                executionStreamPublisher,
                executor,
                guard
        );

        ExecutionResponse response = service.executeAndStreamEvents("hello", "session-1");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getSessionId()).isEqualTo("session-1");
        assertThat(response.getErrorCode()).isEqualTo(ExecutionErrorCodes.SESSION_BUSY);
        assertThat(response.getError()).contains("当前会话正在执行中");
    }

    @Test
    @DisplayName("should release session guard after execution failure so next request can start")
    void shouldReleaseSessionGuardAfterExecutionFailureSoNextRequestCanStart() {
        InMemorySessionExecutionGuard realGuard = new InMemorySessionExecutionGuard();
        when(agentExecutionPort.executeSync(eq("first"), eq("session-1")))
                .thenThrow(new IllegalStateException("boom"));
        when(agentExecutionPort.executeSync(eq("second"), eq("session-1")))
                .thenReturn("ok");

        ExecutionStreamingApplicationService service = new ExecutionStreamingApplicationService(
                agentExecutionPort,
                executionEventPort,
                executionStreamPublisher,
                executor,
                realGuard
        );

        ExecutionResponse firstResponse = service.executeAndStreamEvents("first", "session-1");
        ExecutionResponse secondResponse = service.executeAndStreamEvents("second", "session-1");

        assertThat(firstResponse.isSuccess()).isTrue();
        assertThat(secondResponse.isSuccess()).isTrue();
        assertThat(secondResponse.getErrorCode()).isNull();
        assertThat(realGuard.tryAcquire("session-1")).isTrue();
    }
}
