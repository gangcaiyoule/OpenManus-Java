package com.openmanus.domain.service;

import com.openmanus.agent.workflow.UnifiedWorkflow;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
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
        UnifiedWorkflow workflow = mock(UnifiedWorkflow.class);
        when(workflow.executeSync("hello", "conv-1")).thenReturn("ok");

        AgentService service = new AgentService(workflow);
        CompletableFuture<Map<String, Object>> resultFuture = service.chat("hello", "conv-1", true);
        Map<String, Object> result = resultFuture.join();

        verify(workflow).executeSync("hello", "conv-1");
        assertEquals("ok", result.get("answer"));
        assertEquals("conv-1", result.get("conversationId"));
        assertEquals("unified", result.get("mode"));
    }

    @Test
    void shouldPassConversationIdAsMemoryIdInAsyncMode() {
        UnifiedWorkflow workflow = mock(UnifiedWorkflow.class);
        when(workflow.execute("ping", "conv-2")).thenReturn(CompletableFuture.completedFuture("pong"));

        AgentService service = new AgentService(workflow);
        Map<String, Object> result = service.chat("ping", "conv-2", false).join();

        verify(workflow).execute("ping", "conv-2");
        assertEquals("pong", result.get("answer"));
        assertEquals("conv-2", result.get("conversationId"));
        assertEquals("unified", result.get("mode"));
    }

    @Test
    void shouldRejectEmptyMessage() {
        UnifiedWorkflow workflow = mock(UnifiedWorkflow.class);
        AgentService service = new AgentService(workflow);

        Map<String, Object> result = service.chat("  ", "conv-3", true).join();

        verify(workflow, never()).executeSync("  ", "conv-3");
        assertTrue(String.valueOf(result.get("error")).contains("message不能为空"));
        assertEquals("conv-3", result.get("conversationId"));
    }

    @Test
    void shouldBindSessionIdToMdcInSyncMode() {
        UnifiedWorkflow workflow = mock(UnifiedWorkflow.class);
        AtomicReference<String> observedSessionId = new AtomicReference<>();
        when(workflow.executeSync("hello", "conv-sync")).thenAnswer(invocation -> {
            observedSessionId.set(MDC.get("sessionId"));
            return "ok";
        });

        AgentService service = new AgentService(workflow);
        service.chat("hello", "conv-sync", true).join();

        assertEquals("conv-sync", observedSessionId.get());
    }

    @Test
    void shouldBindSessionIdToMdcInAsyncMode() {
        UnifiedWorkflow workflow = mock(UnifiedWorkflow.class);
        AtomicReference<String> observedSessionId = new AtomicReference<>();
        when(workflow.execute("ping", "conv-async")).thenAnswer(invocation -> {
            observedSessionId.set(MDC.get("sessionId"));
            return CompletableFuture.completedFuture("pong");
        });

        AgentService service = new AgentService(workflow);
        service.chat("ping", "conv-async", false).join();

        assertEquals("conv-async", observedSessionId.get());
    }

    @Test
    void shouldTrimConversationIdBeforePassingToWorkflow() {
        UnifiedWorkflow workflow = mock(UnifiedWorkflow.class);
        when(workflow.executeSync("hello", "conv-trim")).thenReturn("ok");

        AgentService service = new AgentService(workflow);
        Map<String, Object> result = service.chat("hello", "  conv-trim  ", true).join();

        verify(workflow).executeSync("hello", "conv-trim");
        assertEquals("conv-trim", result.get("conversationId"));
    }

    @Test
    void shouldGenerateSafeSessionIdWhenConversationIdContainsIllegalChars() {
        UnifiedWorkflow workflow = mock(UnifiedWorkflow.class);
        AtomicReference<String> observedConversationId = new AtomicReference<>();
        when(workflow.executeSync(anyString(), anyString())).thenAnswer(invocation -> {
            observedConversationId.set(invocation.getArgument(1, String.class));
            return "ok";
        });

        AgentService service = new AgentService(workflow);
        Map<String, Object> result = service.chat("hello", "bad/id", true).join();
        String generatedConversationId = String.valueOf(result.get("conversationId"));

        assertTrue(observedConversationId.get() != null);
        assertTrue(SESSION_ID_PATTERN.matcher(generatedConversationId).matches());
        assertTrue(SESSION_ID_PATTERN.matcher(observedConversationId.get()).matches());
    }
}
