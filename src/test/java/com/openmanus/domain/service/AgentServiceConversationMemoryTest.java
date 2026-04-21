package com.openmanus.domain.service;
import com.openmanus.domain.model.AgentExecutionEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentServiceConversationMemoryTest {
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    @Test
    void shouldPassConversationIdAsMemoryIdInSyncMode() {
        WorkflowExecutionPort workflow = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        when(workflow.executeSync("hello", "conv-1")).thenReturn("ok");

        AgentService service = new AgentService(workflow, executionEventPort);
        CompletableFuture<Map<String, Object>> resultFuture = service.chat("hello", "conv-1", true);
        Map<String, Object> result = resultFuture.join();

        verify(workflow).executeSync("hello", "conv-1");
        verify(executionEventPort).startWorkflowTracking("conv-1", "hello");
        verify(executionEventPort).startExecution("conv-1", "workflow_manager", "WORKFLOW_START", "hello");
        verify(executionEventPort).endWorkflowTracking("conv-1", "ok", true);
        verify(executionEventPort).endExecution(
                "conv-1",
                "workflow_manager",
                "WORKFLOW_COMPLETE",
                "ok",
                AgentExecutionEvent.ExecutionStatus.SUCCESS
        );
        assertEquals("ok", result.get("answer"));
        assertEquals("conv-1", result.get("conversationId"));
        assertEquals("unified", result.get("mode"));
    }

    @Test
    void shouldPassConversationIdAsMemoryIdInAsyncMode() {
        WorkflowExecutionPort workflow = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        when(workflow.execute("ping", "conv-2")).thenReturn(CompletableFuture.completedFuture("pong"));

        AgentService service = new AgentService(workflow, executionEventPort);
        Map<String, Object> result = service.chat("ping", "conv-2", false).join();

        verify(workflow).execute("ping", "conv-2");
        verify(executionEventPort).startWorkflowTracking("conv-2", "ping");
        verify(executionEventPort).startExecution("conv-2", "workflow_manager", "WORKFLOW_START", "ping");
        verify(executionEventPort).endWorkflowTracking("conv-2", "pong", true);
        verify(executionEventPort).endExecution(
                "conv-2",
                "workflow_manager",
                "WORKFLOW_COMPLETE",
                "pong",
                AgentExecutionEvent.ExecutionStatus.SUCCESS
        );
        assertEquals("pong", result.get("answer"));
        assertEquals("conv-2", result.get("conversationId"));
        assertEquals("unified", result.get("mode"));
    }

    @Test
    void shouldRejectEmptyMessage() {
        WorkflowExecutionPort workflow = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        AgentService service = new AgentService(workflow, executionEventPort);

        Map<String, Object> result = service.chat("  ", "conv-3", true).join();

        verify(workflow, never()).executeSync("  ", "conv-3");
        verify(executionEventPort, never()).startWorkflowTracking(anyString(), anyString());
        assertTrue(String.valueOf(result.get("error")).contains("message不能为空"));
        assertEquals("conv-3", result.get("conversationId"));
    }

    @Test
    void shouldBindSessionIdToMdcInSyncMode() {
        WorkflowExecutionPort workflow = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        AtomicReference<String> observedSessionId = new AtomicReference<>();
        when(workflow.executeSync("hello", "conv-sync")).thenAnswer(invocation -> {
            observedSessionId.set(MDC.get("sessionId"));
            return "ok";
        });

        AgentService service = new AgentService(workflow, executionEventPort);
        service.chat("hello", "conv-sync", true).join();

        assertEquals("conv-sync", observedSessionId.get());
    }

    @Test
    void shouldBindSessionIdToMdcInAsyncMode() {
        WorkflowExecutionPort workflow = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        AtomicReference<String> observedSessionId = new AtomicReference<>();
        when(workflow.execute("ping", "conv-async")).thenAnswer(invocation -> {
            observedSessionId.set(MDC.get("sessionId"));
            return CompletableFuture.completedFuture("pong");
        });

        AgentService service = new AgentService(workflow, executionEventPort);
        service.chat("ping", "conv-async", false).join();

        assertEquals("conv-async", observedSessionId.get());
    }

    @Test
    void shouldTrimConversationIdBeforePassingToWorkflow() {
        WorkflowExecutionPort workflow = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        when(workflow.executeSync("hello", "conv-trim")).thenReturn("ok");

        AgentService service = new AgentService(workflow, executionEventPort);
        Map<String, Object> result = service.chat("hello", "  conv-trim  ", true).join();

        verify(workflow).executeSync("hello", "conv-trim");
        assertEquals("conv-trim", result.get("conversationId"));
    }

    @Test
    void shouldGenerateSafeSessionIdWhenConversationIdContainsIllegalChars() {
        WorkflowExecutionPort workflow = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        AtomicReference<String> observedConversationId = new AtomicReference<>();
        when(workflow.executeSync(anyString(), anyString())).thenAnswer(invocation -> {
            observedConversationId.set(invocation.getArgument(1, String.class));
            return "ok";
        });

        AgentService service = new AgentService(workflow, executionEventPort);
        Map<String, Object> result = service.chat("hello", "bad/id", true).join();
        String generatedConversationId = String.valueOf(result.get("conversationId"));

        assertTrue(observedConversationId.get() != null);
        assertTrue(SESSION_ID_PATTERN.matcher(generatedConversationId).matches());
        assertTrue(SESSION_ID_PATTERN.matcher(observedConversationId.get()).matches());
    }

    @Test
    void shouldRecordErrorMonitoringEventsWhenSyncExecutionFails() {
        WorkflowExecutionPort workflow = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        when(workflow.executeSync("hello", "conv-error"))
                .thenThrow(new StacklessIllegalStateException("boom"));

        AgentService service = new AgentService(workflow, executionEventPort);
        Map<String, Object> result = service.chat("hello", "conv-error", true).join();

        verify(executionEventPort).startWorkflowTracking("conv-error", "hello");
        verify(executionEventPort).startExecution("conv-error", "workflow_manager", "WORKFLOW_START", "hello");
        verify(executionEventPort).endWorkflowTracking("conv-error", "执行出错: boom", false);
        verify(executionEventPort).recordError("conv-error", "workflow_manager", "WORKFLOW_EXECUTION", "boom");
        verify(executionEventPort).endExecution(
                "conv-error",
                "workflow_manager",
                "WORKFLOW_COMPLETE",
                "执行出错: boom",
                AgentExecutionEvent.ExecutionStatus.ERROR
        );
        assertEquals("boom", result.get("error"));
    }

    @Test
    void shouldRecordErrorMonitoringEventsWhenAsyncExecutionFails() {
        WorkflowExecutionPort workflow = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        when(workflow.execute("hello", "conv-async-error"))
                .thenReturn(CompletableFuture.failedFuture(new StacklessIllegalStateException("boom")));

        AgentService service = new AgentService(workflow, executionEventPort);
        Map<String, Object> result = service.chat("hello", "conv-async-error", false).join();

        verify(executionEventPort).startWorkflowTracking("conv-async-error", "hello");
        verify(executionEventPort).startExecution("conv-async-error", "workflow_manager", "WORKFLOW_START", "hello");
        verify(executionEventPort).endWorkflowTracking("conv-async-error", "执行出错: boom", false);
        verify(executionEventPort).recordError("conv-async-error", "workflow_manager", "WORKFLOW_EXECUTION", "boom");
        verify(executionEventPort).endExecution(
                "conv-async-error",
                "workflow_manager",
                "WORKFLOW_COMPLETE",
                "执行出错: boom",
                AgentExecutionEvent.ExecutionStatus.ERROR
        );
        assertEquals("boom", result.get("error"));
    }

    @Test
    void shouldRecordErrorMonitoringEventsWhenAsyncExecutionThrowsBeforeReturningFuture() {
        WorkflowExecutionPort workflow = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        when(workflow.execute("hello", "conv-async-throw"))
                .thenThrow(new StacklessIllegalStateException("boom"));

        AgentService service = new AgentService(workflow, executionEventPort);
        Map<String, Object> result = service.chat("hello", "conv-async-throw", false).join();

        verify(executionEventPort).startWorkflowTracking("conv-async-throw", "hello");
        verify(executionEventPort).startExecution("conv-async-throw", "workflow_manager", "WORKFLOW_START", "hello");
        verify(executionEventPort).endWorkflowTracking("conv-async-throw", "执行出错: boom", false);
        verify(executionEventPort).recordError("conv-async-throw", "workflow_manager", "WORKFLOW_EXECUTION", "boom");
        verify(executionEventPort).endExecution(
                "conv-async-throw",
                "workflow_manager",
                "WORKFLOW_COMPLETE",
                "执行出错: boom",
                AgentExecutionEvent.ExecutionStatus.ERROR
        );
        assertEquals("boom", result.get("error"));
    }

    @Test
    void shouldFallbackToUnknownErrorWhenFailureMessageIsBlank() {
        WorkflowExecutionPort workflow = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        when(workflow.executeSync("hello", "conv-null-error"))
                .thenThrow(new StacklessIllegalStateException(null));

        AgentService service = new AgentService(workflow, executionEventPort);
        Map<String, Object> result = service.chat("hello", "conv-null-error", true).join();

        verify(executionEventPort).endWorkflowTracking("conv-null-error", "执行出错: unknown error", false);
        verify(executionEventPort).recordError("conv-null-error", "workflow_manager", "WORKFLOW_EXECUTION", "unknown error");
        verify(executionEventPort).endExecution(
                "conv-null-error",
                "workflow_manager",
                "WORKFLOW_COMPLETE",
                "执行出错: unknown error",
                AgentExecutionEvent.ExecutionStatus.ERROR
        );
        assertEquals("unknown error", result.get("error"));
    }

    @Test
    void shouldUnwrapNestedAsyncCompletionFailure() {
        WorkflowExecutionPort workflow = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        Throwable nested = new CompletionException(new IllegalStateException("deep boom"));
        when(workflow.execute("hello", "conv-nested-error"))
                .thenReturn(CompletableFuture.failedFuture(new CompletionException(nested)));

        AgentService service = new AgentService(workflow, executionEventPort);
        Map<String, Object> result = service.chat("hello", "conv-nested-error", false).join();

        verify(executionEventPort).endWorkflowTracking("conv-nested-error", "执行出错: deep boom", false);
        verify(executionEventPort).recordError("conv-nested-error", "workflow_manager", "WORKFLOW_EXECUTION", "deep boom");
        assertEquals("deep boom", result.get("error"));
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
