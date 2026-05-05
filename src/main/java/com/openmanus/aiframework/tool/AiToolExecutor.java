package com.openmanus.aiframework.tool;

/**
 * Executes a parsed tool request.
 */
@FunctionalInterface
public interface AiToolExecutor {

    String execute(AiToolExecutionRequest request, Object memoryId);
}
