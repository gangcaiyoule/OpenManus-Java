package com.openmanus.domain.service;

import com.openmanus.domain.model.WorkflowErrorCodes;
import com.openmanus.domain.model.WorkflowResponse;
import com.openmanus.domain.model.AgentExecutionEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

import java.util.concurrent.Executor;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowStreamServiceSessionMemoryTest {
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    private WorkflowStreamService createService(WorkflowExecutionPort workflow,
                                                WorkflowExecutionEventPort executionEventPort,
                                                WorkflowStreamPublisher streamPublisher,
                                                Executor executor) {
        return new WorkflowStreamService(workflow, executionEventPort, streamPublisher, executor) {
            @Override
            protected long postExecutionDrainDelayMs() {
                return 0L;
            }
        };
    }

    @Test
    void shouldPassSessionIdToUnifiedWorkflowMemory() {
        WorkflowExecutionPort workflow = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        WorkflowStreamPublisher streamPublisher = mock(WorkflowStreamPublisher.class);
        Executor directExecutor = Runnable::run;

        when(workflow.executeSync("task", "session-123")).thenReturn("done");

        WorkflowStreamService service = createService(workflow, executionEventPort, streamPublisher, directExecutor);

        service.executeWorkflowInternal("task", "session-123", event -> {});

        verify(workflow).executeSync("task", "session-123");
    }

    @Test
    void shouldHandleShortSessionIdWithoutCrashing() {
        WorkflowExecutionPort workflow = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        WorkflowStreamPublisher streamPublisher = mock(WorkflowStreamPublisher.class);
        Executor directExecutor = Runnable::run;

        when(workflow.executeSync("task", "abc")).thenReturn("done");

        WorkflowStreamService service = createService(workflow, executionEventPort, streamPublisher, directExecutor);

        service.executeWorkflowInternal("task", "abc", event -> {});

        verify(workflow, times(1)).executeSync("task", "abc");
    }

    @Test
    void shouldNotLeakMdcWhenSessionIdIsGenerated() {
        WorkflowExecutionPort workflow = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        WorkflowStreamPublisher streamPublisher = mock(WorkflowStreamPublisher.class);
        Executor directExecutor = Runnable::run;

        when(workflow.executeSync(anyString(), anyString())).thenReturn("done");

        WorkflowStreamService service = createService(workflow, executionEventPort, streamPublisher, directExecutor);

        MDC.remove("sessionId");
        var response = service.executeWorkflowAndStreamEvents("task");

        assertNotNull(response.getSessionId());
        assertNull(MDC.get("sessionId"));
    }

    @Test
    void shouldGenerateSessionIdWhenMdcSessionIdIsBlank() {
        WorkflowExecutionPort workflow = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        WorkflowStreamPublisher streamPublisher = mock(WorkflowStreamPublisher.class);
        Executor directExecutor = Runnable::run;

        when(workflow.executeSync(anyString(), anyString())).thenReturn("done");

        WorkflowStreamService service = createService(workflow, executionEventPort, streamPublisher, directExecutor);

        MDC.put("sessionId", "   ");
        WorkflowResponse response = service.executeWorkflowAndStreamEvents("task");
        MDC.remove("sessionId");

        assertTrue(response.isSuccess());
        assertNotNull(response.getSessionId());
        assertFalse(response.getSessionId().isBlank());
        assertNull(response.getError());
        assertNull(response.getErrorCode());
    }

    @Test
    void shouldGenerateSessionIdWhenMdcSessionIdContainsIllegalCharacters() {
        WorkflowExecutionPort workflow = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        WorkflowStreamPublisher streamPublisher = mock(WorkflowStreamPublisher.class);
        Executor directExecutor = Runnable::run;
        when(workflow.executeSync(anyString(), anyString())).thenReturn("done");

        WorkflowStreamService service = createService(workflow, executionEventPort, streamPublisher, directExecutor);

        MDC.put("sessionId", "bad/id");
        WorkflowResponse response = service.executeWorkflowAndStreamEvents("task");
        MDC.remove("sessionId");

        assertTrue(response.isSuccess());
        assertNotNull(response.getSessionId());
        assertTrue(SESSION_ID_PATTERN.matcher(response.getSessionId()).matches());
        assertNull(response.getError());
        assertNull(response.getErrorCode());
    }

    @Test
    void shouldReturnInputInvalidErrorCodeForBlankInput() {
        WorkflowExecutionPort workflow = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        WorkflowStreamPublisher streamPublisher = mock(WorkflowStreamPublisher.class);
        Executor directExecutor = Runnable::run;

        WorkflowStreamService service = createService(workflow, executionEventPort, streamPublisher, directExecutor);

        WorkflowResponse response = service.executeWorkflowAndStreamEvents("   ");

        assertFalse(response.isSuccess());
        assertEquals(WorkflowErrorCodes.INPUT_INVALID, response.getErrorCode());
    }

    @Test
    void shouldRemoveListenerAndReturnErrorWhenAsyncSubmissionRejected() {
        // Expected failure path: service logs an error and returns ASYNC_SUBMIT_REJECTED.
        WorkflowExecutionPort workflow = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        WorkflowStreamPublisher streamPublisher = mock(WorkflowStreamPublisher.class);
        Executor rejectingExecutor = command -> {
            throw new StacklessRejectedExecutionException("queue full");
        };

        WorkflowStreamService service = createService(workflow, executionEventPort, streamPublisher, rejectingExecutor);

        WorkflowResponse response = service.executeWorkflowAndStreamEvents("task");

        assertFalse(response.isSuccess());
        assertNotNull(response.getSessionId());
        assertEquals(WorkflowErrorCodes.ASYNC_SUBMIT_REJECTED, response.getErrorCode());
        verify(executionEventPort, times(1)).addListener(anyString(), org.mockito.ArgumentMatchers.any());
        verify(executionEventPort, times(1)).removeListener(anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRemoveListenerAndReturnErrorWhenAsyncSubmissionThrowsRuntimeException() {
        // Expected failure path: service logs an error and returns ASYNC_SUBMIT_EXCEPTION.
        WorkflowExecutionPort workflow = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        WorkflowStreamPublisher streamPublisher = mock(WorkflowStreamPublisher.class);
        Executor brokenExecutor = command -> {
            throw new StacklessIllegalStateException("executor unavailable");
        };

        WorkflowStreamService service = createService(workflow, executionEventPort, streamPublisher, brokenExecutor);

        WorkflowResponse response = service.executeWorkflowAndStreamEvents("task");

        assertFalse(response.isSuccess());
        assertNotNull(response.getSessionId());
        assertEquals(WorkflowErrorCodes.ASYNC_SUBMIT_EXCEPTION, response.getErrorCode());
        verify(executionEventPort, times(1)).addListener(anyString(), org.mockito.ArgumentMatchers.any());
        verify(executionEventPort, times(1)).removeListener(anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldReturnInternalErrorWhenListenerRegistrationFails() {
        WorkflowExecutionPort workflow = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        WorkflowStreamPublisher streamPublisher = mock(WorkflowStreamPublisher.class);
        Executor directExecutor = Runnable::run;
        doThrow(new StacklessIllegalStateException("tracker unavailable"))
                .when(executionEventPort).addListener(anyString(), org.mockito.ArgumentMatchers.any());

        WorkflowStreamService service = createService(workflow, executionEventPort, streamPublisher, directExecutor);

        WorkflowResponse response = service.executeWorkflowAndStreamEvents("task");

        assertFalse(response.isSuccess());
        assertNotNull(response.getSessionId());
        assertEquals(WorkflowErrorCodes.INTERNAL_ERROR, response.getErrorCode());
        verify(executionEventPort, times(1)).addListener(anyString(), org.mockito.ArgumentMatchers.any());
        verify(executionEventPort, never()).removeListener(anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldForwardOnlyCurrentSessionEventsToWebSocketTopic() {
        WorkflowExecutionPort workflow = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        WorkflowStreamPublisher streamPublisher = mock(WorkflowStreamPublisher.class);
        Executor noOpExecutor = command -> {};

        WorkflowStreamService service = createService(workflow, executionEventPort, streamPublisher, noOpExecutor);

        MDC.put("sessionId", "session-a");
        WorkflowResponse response = service.executeWorkflowAndStreamEvents("task");
        assertTrue(response.isSuccess());

        ArgumentCaptor<WorkflowExecutionEventPort.Listener> listenerCaptor =
                ArgumentCaptor.forClass(WorkflowExecutionEventPort.Listener.class);
        verify(executionEventPort).addListener(eq("session-a"), listenerCaptor.capture());
        WorkflowExecutionEventPort.Listener listener = listenerCaptor.getValue();

        AgentExecutionEvent otherSessionEvent = AgentExecutionEvent.builder().sessionId("session-b").build();
        listener.onEvent(otherSessionEvent);
        verify(streamPublisher, never()).publishEvent(eq("session-a"), any(AgentExecutionEvent.class));

        AgentExecutionEvent currentSessionEvent = AgentExecutionEvent.builder().sessionId("session-a").build();
        listener.onEvent(currentSessionEvent);
        verify(streamPublisher, times(1)).publishEvent("session-a", currentSessionEvent);

        MDC.remove("sessionId");
    }

    @Test
    void shouldRemoveListenerAfterSuccessfulExecution() {
        WorkflowExecutionPort workflow = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        WorkflowStreamPublisher streamPublisher = mock(WorkflowStreamPublisher.class);
        Executor directExecutor = Runnable::run;
        when(workflow.executeSync("task", "session-123")).thenReturn("done");

        WorkflowStreamService service = createService(workflow, executionEventPort, streamPublisher, directExecutor);

        service.executeWorkflowInternal("task", "session-123", event -> { });

        verify(executionEventPort).startWorkflowTracking("session-123", "task");
        verify(executionEventPort).endWorkflowTracking("session-123", "done", true);
        verify(executionEventPort).removeListener(eq("session-123"), any(WorkflowExecutionEventPort.Listener.class));
    }

    @Test
    void shouldRemoveListenerAfterFailedExecution() {
        WorkflowExecutionPort workflow = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        WorkflowStreamPublisher streamPublisher = mock(WorkflowStreamPublisher.class);
        Executor directExecutor = Runnable::run;
        when(workflow.executeSync("task", "session-123"))
                .thenThrow(new StacklessIllegalStateException("boom"));

        WorkflowStreamService service = createService(workflow, executionEventPort, streamPublisher, directExecutor);

        service.executeWorkflowInternal("task", "session-123", event -> { });

        verify(executionEventPort).startWorkflowTracking("session-123", "task");
        verify(executionEventPort).endWorkflowTracking("session-123", "执行出错: boom", false);
        verify(executionEventPort).recordError("session-123", "workflow_manager", "WORKFLOW_EXECUTION", "boom");
        verify(executionEventPort).endExecution(
                "session-123",
                "workflow_manager",
                "WORKFLOW_COMPLETE",
                "执行出错: boom",
                AgentExecutionEvent.ExecutionStatus.ERROR
        );
        verify(executionEventPort).removeListener(eq("session-123"), any(WorkflowExecutionEventPort.Listener.class));
    }

    @Test
    void shouldFallbackToUnknownErrorWhenWorkflowFailureMessageIsBlank() {
        WorkflowExecutionPort workflow = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        WorkflowStreamPublisher streamPublisher = mock(WorkflowStreamPublisher.class);
        Executor directExecutor = Runnable::run;
        when(workflow.executeSync("task", "session-null-error"))
                .thenThrow(new StacklessIllegalStateException(null));

        WorkflowStreamService service = createService(workflow, executionEventPort, streamPublisher, directExecutor);

        service.executeWorkflowInternal("task", "session-null-error", event -> { });

        verify(executionEventPort).endWorkflowTracking("session-null-error", "执行出错: unknown error", false);
        verify(executionEventPort).recordError(
                "session-null-error",
                "workflow_manager",
                "WORKFLOW_EXECUTION",
                "unknown error"
        );
        verify(executionEventPort).endExecution(
                "session-null-error",
                "workflow_manager",
                "WORKFLOW_COMPLETE",
                "执行出错: unknown error",
                AgentExecutionEvent.ExecutionStatus.ERROR
        );
        verify(streamPublisher).publishResult(
                eq("session-null-error"),
                argThat(result -> "执行出错: unknown error".equals(result.getResult()) && "ERROR".equals(result.getStatus()))
        );
    }

    @Test
    void shouldUnwrapNestedWorkflowFailureMessage() {
        WorkflowExecutionPort workflow = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        WorkflowStreamPublisher streamPublisher = mock(WorkflowStreamPublisher.class);
        Executor directExecutor = Runnable::run;
        when(workflow.executeSync("task", "session-wrapped-error"))
                .thenThrow(new CompletionException(new StacklessIllegalStateException("deep boom")));

        WorkflowStreamService service = createService(workflow, executionEventPort, streamPublisher, directExecutor);

        service.executeWorkflowInternal("task", "session-wrapped-error", event -> { });

        verify(executionEventPort).endWorkflowTracking("session-wrapped-error", "执行出错: deep boom", false);
        verify(executionEventPort).recordError(
                "session-wrapped-error",
                "workflow_manager",
                "WORKFLOW_EXECUTION",
                "deep boom"
        );
        verify(executionEventPort).endExecution(
                "session-wrapped-error",
                "workflow_manager",
                "WORKFLOW_COMPLETE",
                "执行出错: deep boom",
                AgentExecutionEvent.ExecutionStatus.ERROR
        );
        verify(streamPublisher).publishResult(
                eq("session-wrapped-error"),
                argThat(result -> "执行出错: deep boom".equals(result.getResult()) && "ERROR".equals(result.getStatus()))
        );
    }

    private static final class StacklessRejectedExecutionException extends RejectedExecutionException {
        private StacklessRejectedExecutionException(String message) {
            super(message);
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

    private static final class StacklessIllegalStateException extends IllegalStateException {
        private StacklessIllegalStateException(String message) {
            super(message);
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }
}
