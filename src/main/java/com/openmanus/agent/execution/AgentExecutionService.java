package com.openmanus.agent.execution;

import com.openmanus.agent.coordination.AgentCoordinator;
import org.slf4j.MDC;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Agent 执行入口，负责封装异步执行与会话上下文。
 */
public class AgentExecutionService {

    private final AgentCoordinator agentCoordinator;
    private final Executor asyncExecutor;

    public AgentExecutionService(AgentCoordinator agentCoordinator, Executor asyncExecutor) {
        this.agentCoordinator = agentCoordinator;
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

        Object memoryId = conversationId != null && !conversationId.isBlank()
                ? conversationId
                : UUID.randomUUID();
        try (MDC.MDCCloseable ignored = MDC.putCloseable("sessionId", String.valueOf(memoryId))) {
            return agentCoordinator.execute(userInput, memoryId);
        }
    }

    public String executeSync(String userInput) {
        return executeSync(userInput, null);
    }
}
