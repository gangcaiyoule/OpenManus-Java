package com.openmanus.infra.workflow;

import com.openmanus.agent.workflow.UnifiedWorkflow;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnifiedWorkflowPortAdapterTest {

    @Test
    void shouldDelegateAsyncExecution() {
        UnifiedWorkflow workflow = mock(UnifiedWorkflow.class);
        UnifiedWorkflowPortAdapter adapter = new UnifiedWorkflowPortAdapter(workflow);
        CompletableFuture<String> result = CompletableFuture.completedFuture("ok");

        when(workflow.execute("hello", "conv-1")).thenReturn(result);

        assertSame(result, adapter.execute("hello", "conv-1"));
    }

    @Test
    void shouldDelegateSyncExecution() {
        UnifiedWorkflow workflow = mock(UnifiedWorkflow.class);
        UnifiedWorkflowPortAdapter adapter = new UnifiedWorkflowPortAdapter(workflow);

        when(workflow.executeSync("hello", "conv-1")).thenReturn("done");

        assertSame("done", adapter.executeSync("hello", "conv-1"));
    }
}
