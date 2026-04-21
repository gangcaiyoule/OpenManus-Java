package com.openmanus.domain.service;

import java.util.concurrent.CompletableFuture;

public interface WorkflowExecutionPort {

    CompletableFuture<String> execute(String userInput, String conversationId);

    String executeSync(String userInput, String conversationId);
}
