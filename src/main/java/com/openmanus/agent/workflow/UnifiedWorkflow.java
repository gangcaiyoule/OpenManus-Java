package com.openmanus.agent.workflow;

import com.openmanus.agent.impl.unified.UnifiedAgent;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 统一工作流入口，底层仅使用一个 UnifiedAgent。
 */
@Service
public class UnifiedWorkflow {

    private final UnifiedAgent unifiedAgent;
    private final Executor asyncExecutor;

    public UnifiedWorkflow(UnifiedAgent unifiedAgent,
                           @Qualifier("asyncExecutor") Executor asyncExecutor) {
        this.unifiedAgent = unifiedAgent;
        this.asyncExecutor = asyncExecutor;
    }

    public CompletableFuture<String> execute(String userInput, String conversationId) {
        return CompletableFuture.supplyAsync(() -> executeSync(userInput, conversationId), asyncExecutor);
    }

    public CompletableFuture<String> execute(String userInput) {
        return CompletableFuture.supplyAsync(() -> executeSync(userInput), asyncExecutor);
    }

    public String executeSync(String userInput, String conversationId) {
        if (userInput == null || userInput.isBlank()) {
            throw new IllegalArgumentException("userInput cannot be null or blank");
        }
        ToolExecutionRequest initialRequest = ToolExecutionRequest.builder()
                .name(unifiedAgent.name())
                .arguments(userInput)
                .build();

        Object memoryId = conversationId != null && !conversationId.isBlank()
                ? conversationId
                : UUID.randomUUID();
        try (MDC.MDCCloseable ignored = MDC.putCloseable("sessionId", String.valueOf(memoryId))) {
            return unifiedAgent.execute(initialRequest, memoryId);
        }
    }

    public String executeSync(String userInput) {
        return executeSync(userInput, null);
    }
}
