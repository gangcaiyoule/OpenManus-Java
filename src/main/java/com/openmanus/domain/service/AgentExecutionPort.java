package com.openmanus.domain.service;

import java.util.concurrent.CompletableFuture;

public interface AgentExecutionPort {

    CompletableFuture<String> execute(String userInput, String conversationId);

    String executeSync(String userInput, String conversationId);
}
