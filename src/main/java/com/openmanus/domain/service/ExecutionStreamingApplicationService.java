package com.openmanus.domain.service;

import com.openmanus.domain.model.ExecutionErrorCodes;
import com.openmanus.domain.model.ExecutionResponse;
import com.openmanus.domain.model.ExecutionResultView;
import com.openmanus.domain.model.AgentExecutionEvent;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Agent 执行链路的流式应用服务（WebSocket 推送）。
 */
@Slf4j
public class ExecutionStreamingApplicationService {
    private static final String SESSION_ID_KEY = "sessionId";
    private static final String EXECUTION_COORDINATOR = "execution_coordinator";
    private static final String EXECUTION_START = "EXECUTION_START";
    private static final String EXECUTION_COMPLETE = "EXECUTION_COMPLETE";
    private static final String EXECUTION_ERROR = "EXECUTION_ERROR";

    private final AgentExecutionPort agentExecutionPort;
    private final ExecutionEventPort executionEventPort;
    private final ExecutionStreamPublisher streamPublisher;
    private final Executor asyncExecutor; // 注入自定义线程池

    public ExecutionStreamingApplicationService(AgentExecutionPort agentExecutionPort,
                                                ExecutionEventPort executionEventPort,
                                                ExecutionStreamPublisher streamPublisher,
                                                Executor asyncExecutor) {
        this.agentExecutionPort = agentExecutionPort;
        this.executionEventPort = executionEventPort;
        this.streamPublisher = streamPublisher;
        this.asyncExecutor = asyncExecutor;
    }

    /**
     * 以流式方式执行统一执行链路，并通过 WebSocket 发送事件。
     *
     * @param userInput 用户输入
     * @return 包含 sessionId 的 ExecutionResponse，用于客户端订阅
     */
    public ExecutionResponse executeAndStreamEvents(String userInput) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return ExecutionResponse.builder()
                    .success(false)
                    .error("输入不能为空")
                    .errorCode(ExecutionErrorCodes.INPUT_INVALID)
                    .build();
        }

        String sessionId = resolveSessionId();

        final String currentSessionId = sessionId;

        ExecutionEventPort.Listener listener = event -> {
            if (event == null || !currentSessionId.equals(event.getSessionId())) {
                return;
            }
            log.debug("Sending event for session {}: {}", currentSessionId, event);
            streamPublisher.publishEvent(currentSessionId, event);
        };
        try {
            executionEventPort.addListener(currentSessionId, listener);
        } catch (RuntimeException e) {
            log.error("监听器注册异常: sessionId={}", sessionId, e);
            return ExecutionResponse.builder()
                    .success(false)
                    .sessionId(sessionId)
                    .error("内部错误，请稍后重试")
                    .errorCode(ExecutionErrorCodes.INTERNAL_ERROR)
                    .build();
        }

        try {
            // 直接使用注入的Executor来异步执行任务
            final String finalSessionId = sessionId;
            asyncExecutor.execute(() -> executeExecutionInternal(userInput, finalSessionId, listener));
        } catch (RejectedExecutionException e) {
            removeListenerSafely(currentSessionId, listener);
            log.error("异步任务提交失败: sessionId={}", sessionId, e);
            return ExecutionResponse.builder()
                    .success(false)
                    .sessionId(sessionId)
                    .error("任务提交失败，请稍后重试")
                    .errorCode(ExecutionErrorCodes.ASYNC_SUBMIT_REJECTED)
                    .build();
        } catch (RuntimeException e) {
            removeListenerSafely(currentSessionId, listener);
            log.error("异步任务提交异常: sessionId={}", sessionId, e);
            return ExecutionResponse.builder()
                    .success(false)
                    .sessionId(sessionId)
                    .error("任务提交异常，请稍后重试")
                    .errorCode(ExecutionErrorCodes.ASYNC_SUBMIT_EXCEPTION)
                    .build();
        }

        return ExecutionResponse.builder()
                .success(true)
                .sessionId(sessionId)
                .build();
    }

    /**
     * 执行链路的核心同步逻辑
     * @param userInput 用户输入
     * @param sessionId 会话ID
     * @param listener 事件监听器
     */
    public void executeExecutionInternal(String userInput, String sessionId, ExecutionEventPort.Listener listener) {
        final LocalDateTime startTime = LocalDateTime.now();

        try (MDC.MDCCloseable ignored = MDC.putCloseable(SESSION_ID_KEY, sessionId)) {
            log.info("Execution started: sessionId={}", sessionId);
            executionEventPort.startExecutionTracking(sessionId, userInput);
            executionEventPort.startExecution(sessionId, EXECUTION_COORDINATOR, EXECUTION_START, userInput);
            String result = agentExecutionPort.executeSync(userInput, sessionId);
            executionEventPort.endExecutionTracking(sessionId, result, true);

            executionEventPort.endExecution(
                    sessionId,
                    EXECUTION_COORDINATOR,
                    EXECUTION_COMPLETE,
                    result,
                    "SUCCESS"
            );

            LocalDateTime endTime = LocalDateTime.now();
            long executionTimeMs = ChronoUnit.MILLIS.between(startTime, endTime);
            log.info("Execution completed: sessionId={}, durationMs={}", sessionId, executionTimeMs);
            sendExecutionResult(sessionId, userInput, result, "SUCCESS", endTime, executionTimeMs);

        } catch (RuntimeException e) {
            Throwable actualError = unwrapException(e);
            String errorMessage = safeErrorMessage(actualError);
            log.error("Execution failed: sessionId={}", sessionId, e);
            executionEventPort.endExecutionTracking(sessionId, "执行出错: " + errorMessage, false);
            executionEventPort.recordError(sessionId, EXECUTION_COORDINATOR, EXECUTION_ERROR, errorMessage);
            executionEventPort.endExecution(
                    sessionId,
                    EXECUTION_COORDINATOR,
                    EXECUTION_COMPLETE,
                    "执行出错: " + errorMessage,
                    "ERROR"
            );

            long executionTimeMs = ChronoUnit.MILLIS.between(startTime, LocalDateTime.now());
            sendExecutionResult(sessionId, userInput, "执行出错: " + errorMessage, "ERROR", LocalDateTime.now(), executionTimeMs);
        } finally {
            try {
                long delayMs = postExecutionDrainDelayMs();
                if (delayMs > 0) {
                    Thread.sleep(delayMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            log.debug("Execution cleanup: sessionId={}", sessionId);
            removeListenerSafely(sessionId, listener);
        }
    }

    protected long postExecutionDrainDelayMs() {
        return 0L;
    }

    /**
     * 发送执行结果。
     */
    private void sendExecutionResult(String sessionId, String userInput, String result,
                                     String status, LocalDateTime completedTime, long executionTimeMs) {
        ExecutionResultView resultVO = ExecutionResultView.builder()
                .sessionId(sessionId)
                .userInput(userInput)
                .result(result)
                .status(status)
                .completedTime(completedTime)
                .executionTime(executionTimeMs)
                .build();

        try {
            log.debug("发送执行结果: sessionId={}", sessionId);
            streamPublisher.publishResult(sessionId, resultVO);
        } catch (Exception e) {
            log.debug("无法发送结果到会话 {}: {}", sessionId, e.getMessage());
        }
    }

    private String resolveSessionId() {
        String sessionId = normalizeSessionId(MDC.get(SESSION_ID_KEY));
        if (sessionId == null) {
            log.warn("MDC中未找到有效sessionId，将生成一个新的。请检查拦截器配置。");
            return UUID.randomUUID().toString();
        }
        return sessionId;
    }

    static String normalizeSessionId(String rawSessionId) {
        return SessionIdPolicy.normalizeOrNull(rawSessionId);
    }

    private void removeListenerSafely(String sessionId, ExecutionEventPort.Listener listener) {
        try {
            executionEventPort.removeListener(sessionId, listener);
        } catch (RuntimeException e) {
            log.warn("清理监听器异常: sessionId={}", sessionId, e);
        }
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
} 
