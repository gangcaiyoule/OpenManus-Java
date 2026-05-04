package com.openmanus.domain.service;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Agent 执行链路的 HTTP 对话服务。
 */
@Slf4j
public class ConversationApplicationService {
    private static final String EXECUTION_COORDINATOR = "execution_coordinator";
    private static final String EXECUTION_START = "EXECUTION_START";
    private static final String EXECUTION_COMPLETE = "EXECUTION_COMPLETE";
    private static final String EXECUTION_ERROR = "EXECUTION_ERROR";
    private static final String DEFAULT_USER_ID = "001";

    private final AgentExecutionPort agentExecutionPort;
    private final ExecutionEventPort executionEventPort;

    public ConversationApplicationService(AgentExecutionPort agentExecutionPort,
                                          ExecutionEventPort executionEventPort) {
        this.agentExecutionPort = agentExecutionPort;
        this.executionEventPort = executionEventPort;
    }

    /**
     * 处理 Agent 对话（统一执行链路）。
     *
     * @param message 用户消息
     * @param conversationId 会话 ID，如果为 null 则创建新会话
     * @param sync 是否同步执行
     * @return 对话结果
     */
    public CompletableFuture<Map<String, Object>> chat(String message, String conversationId, boolean sync) {
        final String sessionId = normalizeSessionId(conversationId);
        final String userId = currentUserId();

        if (message == null || message.trim().isEmpty()) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", "message不能为空");
            errorResult.put("conversationId", sessionId);
            return CompletableFuture.completedFuture(errorResult);
        }
        
        if (sync) {
            return CompletableFuture.completedFuture(executeSyncWithMonitoring(message, sessionId));
        } else {
            try (MDC.MDCCloseable ignoredSession = MDC.putCloseable("sessionId", sessionId);
                 MDC.MDCCloseable ignoredUser = MDC.putCloseable("userId", userId)) {
                startMonitoring(sessionId, message);
                CompletableFuture<String> executionFuture = agentExecutionPort.execute(message, sessionId);
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
        try (MDC.MDCCloseable ignoredSession = MDC.putCloseable("sessionId", sessionId);
             MDC.MDCCloseable ignoredUser = MDC.putCloseable("userId", currentUserId())) {
            startMonitoring(sessionId, message);
            String response = agentExecutionPort.executeSync(message, sessionId);
            return completeSuccess(sessionId, response);
        } catch (Exception e) {
            return completeFailure(sessionId, e, "Error in sync chat execution");
        }
    }

    private void startMonitoring(String sessionId, String message) {
        executionEventPort.startExecutionTracking(sessionId, message);
        executionEventPort.startExecution(sessionId, EXECUTION_COORDINATOR, EXECUTION_START, message);
    }

    private Map<String, Object> completeSuccess(String sessionId, String response) {
        executionEventPort.endExecutionTracking(sessionId, response, true);
        executionEventPort.endExecution(
                sessionId,
                EXECUTION_COORDINATOR,
                EXECUTION_COMPLETE,
                response,
                "SUCCESS"
        );
        return successResult(sessionId, response);
    }

    private Map<String, Object> completeFailure(String sessionId, Throwable error, String logMessage) {
        Throwable actualError = unwrapException(error);
        String message = safeErrorMessage(actualError);
        log.error(logMessage, actualError);
        executionEventPort.endExecutionTracking(sessionId, "执行出错: " + message, false);
        executionEventPort.recordError(sessionId, EXECUTION_COORDINATOR, EXECUTION_ERROR, message);
        executionEventPort.endExecution(
                sessionId,
                EXECUTION_COORDINATOR,
                EXECUTION_COMPLETE,
                "执行出错: " + message,
                "ERROR"
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
        result.put("mode", "agent");
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

    private static String currentUserId() {
        String userId = MDC.get("userId");
        return userId == null || userId.isBlank() ? DEFAULT_USER_ID : userId;
    }
}
