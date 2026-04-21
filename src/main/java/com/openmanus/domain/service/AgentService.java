package com.openmanus.domain.service;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 统一工作流的 HTTP 对话服务。
 */
@Slf4j
public class AgentService {
    private static final String WORKFLOW_MANAGER = "workflow_manager";
    private static final String WORKFLOW_START = "WORKFLOW_START";
    private static final String WORKFLOW_COMPLETE = "WORKFLOW_COMPLETE";
    private static final String WORKFLOW_EXECUTION = "WORKFLOW_EXECUTION";

    private final WorkflowExecutionPort workflowExecutionPort;
    private final WorkflowExecutionEventPort executionEventPort;

    public AgentService(WorkflowExecutionPort workflowExecutionPort,
                        WorkflowExecutionEventPort executionEventPort) {
        this.workflowExecutionPort = workflowExecutionPort;
        this.executionEventPort = executionEventPort;
    }

    /**
     * 处理 Agent 对话（统一单智能体工作流）。
     *
     * @param message 用户消息
     * @param conversationId 会话 ID，如果为 null 则创建新会话
     * @param sync 是否同步执行
     * @return 对话结果
     */
    public CompletableFuture<Map<String, Object>> chat(String message, String conversationId, boolean sync) {
        final String sessionId = normalizeSessionId(conversationId);

        if (message == null || message.trim().isEmpty()) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", "message不能为空");
            errorResult.put("conversationId", sessionId);
            return CompletableFuture.completedFuture(errorResult);
        }
        
        if (sync) {
            return CompletableFuture.completedFuture(executeSyncWithMonitoring(message, sessionId));
        } else {
            try (MDC.MDCCloseable ignored = MDC.putCloseable("sessionId", sessionId)) {
                startMonitoring(sessionId, message);
                CompletableFuture<String> executionFuture = workflowExecutionPort.execute(message, sessionId);
                return executionFuture
                        .thenApply(response -> completeSuccess(sessionId, response))
                        .exceptionally(e -> completeFailure(sessionId, unwrapException(e), "Error in async chat execution"));
            } catch (Exception e) {
                return CompletableFuture.completedFuture(
                        completeFailure(sessionId, e, "Error before async chat execution started")
                );
            }
        }
    }

    private Map<String, Object> executeSyncWithMonitoring(String message, String sessionId) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("sessionId", sessionId)) {
            startMonitoring(sessionId, message);
            String response = workflowExecutionPort.executeSync(message, sessionId);
            return completeSuccess(sessionId, response);
        } catch (Exception e) {
            return completeFailure(sessionId, e, "Error in sync chat execution");
        }
    }

    private void startMonitoring(String sessionId, String message) {
        executionEventPort.startWorkflowTracking(sessionId, message);
        executionEventPort.startExecution(sessionId, WORKFLOW_MANAGER, WORKFLOW_START, message);
    }

    private Map<String, Object> completeSuccess(String sessionId, String response) {
        executionEventPort.endWorkflowTracking(sessionId, response, true);
        executionEventPort.endExecution(
                sessionId,
                WORKFLOW_MANAGER,
                WORKFLOW_COMPLETE,
                response,
                com.openmanus.domain.model.AgentExecutionEvent.ExecutionStatus.SUCCESS
        );
        return successResult(sessionId, response);
    }

    private Map<String, Object> completeFailure(String sessionId, Throwable error, String logMessage) {
        Throwable actualError = unwrapException(error);
        String message = safeErrorMessage(actualError);
        log.error(logMessage, actualError);
        executionEventPort.endWorkflowTracking(sessionId, "执行出错: " + message, false);
        executionEventPort.recordError(sessionId, WORKFLOW_MANAGER, WORKFLOW_EXECUTION, message);
        executionEventPort.endExecution(
                sessionId,
                WORKFLOW_MANAGER,
                WORKFLOW_COMPLETE,
                "执行出错: " + message,
                com.openmanus.domain.model.AgentExecutionEvent.ExecutionStatus.ERROR
        );
        return errorResult(sessionId, message);
    }

    private static Throwable unwrapException(Throwable throwable) {
        if (throwable == null) {
            return new IllegalStateException("unknown error");
        }
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static String safeErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return "unknown error";
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return "unknown error";
        }
        return message.trim();
    }

    private static Map<String, Object> successResult(String sessionId, String response) {
        Map<String, Object> result = new HashMap<>();
        result.put("answer", response);
        result.put("conversationId", sessionId);
        result.put("timestamp", LocalDateTime.now().toString());
        result.put("mode", "unified");
        return result;
    }

    private static Map<String, Object> errorResult(String sessionId, String message) {
        Map<String, Object> errorResult = new HashMap<>();
        errorResult.put("error", message);
        errorResult.put("conversationId", sessionId);
        return errorResult;
    }

    static String normalizeSessionId(String rawConversationId) {
        return SessionIdPolicy.normalizeOrGenerate(rawConversationId);
    }
}
