package com.openmanus.infra.execution;

import com.openmanus.agent.execution.AgentExecutionService;
import com.openmanus.domain.service.AgentExecutionPort;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class AgentExecutionAdapter implements AgentExecutionPort {

    private final AgentExecutionService agentExecutionService;

    public AgentExecutionAdapter(AgentExecutionService agentExecutionService) {
        this.agentExecutionService = agentExecutionService;
    }

    @Override
    public CompletableFuture<String> execute(String userInput, String conversationId) {
        return agentExecutionService.execute(userInput, conversationId);
    }

    @Override
    public String executeSync(String userInput, String conversationId) {
        return agentExecutionService.executeSync(userInput, conversationId);
    }
}
