package com.openmanus.agent.workflow;

import com.openmanus.agent.impl.unified.UnifiedAgent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.SyncTaskExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UnifiedWorkflowTest {

    @Test
    void shouldRejectBlankInput() {
        UnifiedAgent unifiedAgent = mock(UnifiedAgent.class);
        UnifiedWorkflow workflow = new UnifiedWorkflow(unifiedAgent, new SyncTaskExecutor());

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> workflow.executeSync("   ", "conv-1"));
        assertEquals("userInput cannot be null or blank", ex.getMessage());
        verify(unifiedAgent, never()).execute(any(String.class), any());
    }

    @Test
    void shouldRejectNullInput() {
        UnifiedAgent unifiedAgent = mock(UnifiedAgent.class);
        UnifiedWorkflow workflow = new UnifiedWorkflow(unifiedAgent, new SyncTaskExecutor());

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> workflow.executeSync(null, "conv-1"));
        assertEquals("userInput cannot be null or blank", ex.getMessage());
        verify(unifiedAgent, never()).execute(any(String.class), any());
    }

    @Test
    void shouldPassConversationIdAsMemoryId() {
        UnifiedAgent unifiedAgent = mock(UnifiedAgent.class);
        when(unifiedAgent.execute(any(String.class), eq("conv-42"))).thenReturn("ok");

        UnifiedWorkflow workflow = new UnifiedWorkflow(unifiedAgent, new SyncTaskExecutor());
        String result = workflow.executeSync("hello", "conv-42");

        assertEquals("ok", result);
        verify(unifiedAgent).execute("hello", "conv-42");
    }

    @Test
    void shouldPassUserInputToUnifiedAgentDirectly() {
        UnifiedAgent unifiedAgent = mock(UnifiedAgent.class);
        when(unifiedAgent.execute(any(String.class), eq("conv-x"))).thenReturn("ok");

        UnifiedWorkflow workflow = new UnifiedWorkflow(unifiedAgent, new SyncTaskExecutor());
        String result = workflow.executeSync("hello-world", "conv-x");

        assertEquals("ok", result);
        verify(unifiedAgent).execute("hello-world", "conv-x");
    }

    @Test
    void shouldGenerateMemoryIdWhenConversationIdMissing() {
        UnifiedAgent unifiedAgent = mock(UnifiedAgent.class);
        when(unifiedAgent.execute(any(String.class), any())).thenReturn("ok");

        UnifiedWorkflow workflow = new UnifiedWorkflow(unifiedAgent, new SyncTaskExecutor());
        String result = workflow.executeSync("hello");

        assertEquals("ok", result);
        ArgumentCaptor<Object> memoryIdCaptor = ArgumentCaptor.forClass(Object.class);
        verify(unifiedAgent).execute(eq("hello"), memoryIdCaptor.capture());
        assertNotNull(memoryIdCaptor.getValue());
    }
}
