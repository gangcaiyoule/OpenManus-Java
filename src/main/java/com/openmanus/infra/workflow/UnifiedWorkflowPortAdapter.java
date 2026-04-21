package com.openmanus.infra.workflow;

import com.openmanus.agent.workflow.UnifiedWorkflow;
import com.openmanus.domain.service.WorkflowExecutionPort;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class UnifiedWorkflowPortAdapter implements WorkflowExecutionPort {

    private final UnifiedWorkflow unifiedWorkflow;

    public UnifiedWorkflowPortAdapter(UnifiedWorkflow unifiedWorkflow) {
        this.unifiedWorkflow = unifiedWorkflow;
    }

    @Override
    public CompletableFuture<String> execute(String userInput, String conversationId) {
        return unifiedWorkflow.execute(userInput, conversationId);
    }

    @Override
    public String executeSync(String userInput, String conversationId) {
        return unifiedWorkflow.executeSync(userInput, conversationId);
    }
}
