package com.openmanus.domain.service;

import com.openmanus.agent.workflow.UnifiedWorkflow;
import com.openmanus.domain.model.AgentExecutionEvent;
import com.openmanus.domain.model.WorkflowErrorCodes;
import com.openmanus.domain.model.WorkflowResponse;
import com.openmanus.infra.monitoring.AgentExecutionTracker;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class WorkflowStreamServiceSessionMemoryTest {
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    private WorkflowStreamService createService(UnifiedWorkflow workflow,
                                                AgentExecutionTracker tracker,
                                                SimpMessagingTemplate messagingTemplate,
                                                Executor executor) {
        return new WorkflowStreamService(workflow, tracker, messagingTemplate, executor) {
            @Override
            protected long postExecutionDrainDelayMs() {
                return 0L;
            }
        };
    }

    @Test
    void shouldPassSessionIdToUnifiedWorkflowMemory() {
        UnifiedWorkflow workflow = mock(UnifiedWorkflow.class);
        AgentExecutionTracker tracker = mock(AgentExecutionTracker.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        Executor directExecutor = Runnable::run;

        when(workflow.executeSync("task", "session-123")).thenReturn("done");

        WorkflowStreamService service = createService(workflow, tracker, messagingTemplate, directExecutor);

        service.executeWorkflowInternal("task", "session-123", event -> {});

        verify(workflow).executeSync("task", "session-123");
    }

    @Test
    void shouldHandleShortSessionIdWithoutCrashing() {
        UnifiedWorkflow workflow = mock(UnifiedWorkflow.class);
        AgentExecutionTracker tracker = mock(AgentExecutionTracker.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        Executor directExecutor = Runnable::run;

        when(workflow.executeSync("task", "abc")).thenReturn("done");

        WorkflowStreamService service = createService(workflow, tracker, messagingTemplate, directExecutor);

        service.executeWorkflowInternal("task", "abc", event -> {});

        verify(workflow, times(1)).executeSync("task", "abc");
    }

    @Test
    void shouldNotLeakMdcWhenSessionIdIsGenerated() {
        UnifiedWorkflow workflow = mock(UnifiedWorkflow.class);
        AgentExecutionTracker tracker = mock(AgentExecutionTracker.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        Executor directExecutor = Runnable::run;

        when(workflow.executeSync(anyString(), anyString())).thenReturn("done");

        WorkflowStreamService service = createService(workflow, tracker, messagingTemplate, directExecutor);

        MDC.remove("sessionId");
        var response = service.executeWorkflowAndStreamEvents("task");

        assertNotNull(response.getSessionId());
        assertNull(MDC.get("sessionId"));
    }

    @Test
    void shouldGenerateSessionIdWhenMdcSessionIdIsBlank() {
        UnifiedWorkflow workflow = mock(UnifiedWorkflow.class);
        AgentExecutionTracker tracker = mock(AgentExecutionTracker.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        Executor directExecutor = Runnable::run;

        when(workflow.executeSync(anyString(), anyString())).thenReturn("done");

        WorkflowStreamService service = createService(workflow, tracker, messagingTemplate, directExecutor);

        MDC.put("sessionId", "   ");
        WorkflowResponse response = service.executeWorkflowAndStreamEvents("task");
        MDC.remove("sessionId");

        assertTrue(response.isSuccess());
        assertNotNull(response.getSessionId());
        assertFalse(response.getSessionId().isBlank());
        assertEquals("/topic/executions/" + response.getSessionId(), response.getTopic());
    }

    @Test
    void shouldGenerateSessionIdWhenMdcSessionIdContainsIllegalCharacters() {
        UnifiedWorkflow workflow = mock(UnifiedWorkflow.class);
        AgentExecutionTracker tracker = mock(AgentExecutionTracker.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        Executor directExecutor = Runnable::run;
        when(workflow.executeSync(anyString(), anyString())).thenReturn("done");

        WorkflowStreamService service = createService(workflow, tracker, messagingTemplate, directExecutor);

        MDC.put("sessionId", "bad/id");
        WorkflowResponse response = service.executeWorkflowAndStreamEvents("task");
        MDC.remove("sessionId");

        assertTrue(response.isSuccess());
        assertNotNull(response.getSessionId());
        assertTrue(SESSION_ID_PATTERN.matcher(response.getSessionId()).matches());
        assertEquals("/topic/executions/" + response.getSessionId(), response.getTopic());
    }

    @Test
    void shouldReturnInputInvalidErrorCodeForBlankInput() {
        UnifiedWorkflow workflow = mock(UnifiedWorkflow.class);
        AgentExecutionTracker tracker = mock(AgentExecutionTracker.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        Executor directExecutor = Runnable::run;

        WorkflowStreamService service = createService(workflow, tracker, messagingTemplate, directExecutor);

        WorkflowResponse response = service.executeWorkflowAndStreamEvents("   ");

        assertFalse(response.isSuccess());
        assertEquals(WorkflowErrorCodes.INPUT_INVALID, response.getErrorCode());
    }

    @Test
    void shouldRemoveListenerAndReturnErrorWhenAsyncSubmissionRejected() {
        // Expected failure path: service logs an error and returns ASYNC_SUBMIT_REJECTED.
        UnifiedWorkflow workflow = mock(UnifiedWorkflow.class);
        AgentExecutionTracker tracker = mock(AgentExecutionTracker.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        Executor rejectingExecutor = command -> {
            throw new StacklessRejectedExecutionException("queue full");
        };

        WorkflowStreamService service = createService(workflow, tracker, messagingTemplate, rejectingExecutor);

        WorkflowResponse response = service.executeWorkflowAndStreamEvents("task");

        assertFalse(response.isSuccess());
        assertNotNull(response.getSessionId());
        assertEquals(WorkflowErrorCodes.ASYNC_SUBMIT_REJECTED, response.getErrorCode());
        verify(tracker, times(1)).addListener(anyString(), org.mockito.ArgumentMatchers.any());
        verify(tracker, times(1)).removeListener(anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRemoveListenerAndReturnErrorWhenAsyncSubmissionThrowsRuntimeException() {
        // Expected failure path: service logs an error and returns ASYNC_SUBMIT_EXCEPTION.
        UnifiedWorkflow workflow = mock(UnifiedWorkflow.class);
        AgentExecutionTracker tracker = mock(AgentExecutionTracker.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        Executor brokenExecutor = command -> {
            throw new StacklessIllegalStateException("executor unavailable");
        };

        WorkflowStreamService service = createService(workflow, tracker, messagingTemplate, brokenExecutor);

        WorkflowResponse response = service.executeWorkflowAndStreamEvents("task");

        assertFalse(response.isSuccess());
        assertNotNull(response.getSessionId());
        assertEquals(WorkflowErrorCodes.ASYNC_SUBMIT_EXCEPTION, response.getErrorCode());
        verify(tracker, times(1)).addListener(anyString(), org.mockito.ArgumentMatchers.any());
        verify(tracker, times(1)).removeListener(anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldReturnInternalErrorWhenListenerRegistrationFails() {
        UnifiedWorkflow workflow = mock(UnifiedWorkflow.class);
        AgentExecutionTracker tracker = mock(AgentExecutionTracker.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        Executor directExecutor = Runnable::run;
        doThrow(new StacklessIllegalStateException("tracker unavailable"))
                .when(tracker).addListener(anyString(), org.mockito.ArgumentMatchers.any());

        WorkflowStreamService service = createService(workflow, tracker, messagingTemplate, directExecutor);

        WorkflowResponse response = service.executeWorkflowAndStreamEvents("task");

        assertFalse(response.isSuccess());
        assertNotNull(response.getSessionId());
        assertEquals(WorkflowErrorCodes.INTERNAL_ERROR, response.getErrorCode());
        verify(tracker, times(1)).addListener(anyString(), org.mockito.ArgumentMatchers.any());
        verify(tracker, never()).removeListener(anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldForwardOnlyCurrentSessionEventsToWebSocketTopic() {
        UnifiedWorkflow workflow = mock(UnifiedWorkflow.class);
        AgentExecutionTracker tracker = mock(AgentExecutionTracker.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        Executor noOpExecutor = command -> {};

        WorkflowStreamService service = createService(workflow, tracker, messagingTemplate, noOpExecutor);

        MDC.put("sessionId", "session-a");
        WorkflowResponse response = service.executeWorkflowAndStreamEvents("task");
        assertTrue(response.isSuccess());

        ArgumentCaptor<AgentExecutionTracker.AgentExecutionEventListener> listenerCaptor =
                ArgumentCaptor.forClass(AgentExecutionTracker.AgentExecutionEventListener.class);
        verify(tracker).addListener(eq("session-a"), listenerCaptor.capture());
        AgentExecutionTracker.AgentExecutionEventListener listener = listenerCaptor.getValue();

        AgentExecutionEvent otherSessionEvent = AgentExecutionEvent.builder()
                .sessionId("session-b")
                .build();
        listener.onEvent(otherSessionEvent);
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/executions/session-a"), any(AgentExecutionEvent.class));

        AgentExecutionEvent currentSessionEvent = AgentExecutionEvent.builder()
                .sessionId("session-a")
                .build();
        listener.onEvent(currentSessionEvent);
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/executions/session-a"), eq(currentSessionEvent));

        MDC.remove("sessionId");
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
