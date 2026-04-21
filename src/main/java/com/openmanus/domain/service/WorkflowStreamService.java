package com.openmanus.domain.service;

import com.openmanus.domain.model.WorkflowErrorCodes;
import com.openmanus.domain.model.WorkflowResponse;
import com.openmanus.domain.model.WorkflowResultVO;
import com.openmanus.domain.model.AgentExecutionEvent;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 统一工作流的流式执行服务（WebSocket 推送）。
 */
@Slf4j
public class WorkflowStreamService {
    private static final String SESSION_ID_KEY = "sessionId";

    private final WorkflowExecutionPort workflowExecutionPort;
    private final WorkflowExecutionEventPort executionEventPort;
    private final WorkflowStreamPublisher streamPublisher;
    private final Executor asyncExecutor; // 注入自定义线程池

    public WorkflowStreamService(WorkflowExecutionPort workflowExecutionPort,
                                 WorkflowExecutionEventPort executionEventPort,
                                 WorkflowStreamPublisher streamPublisher,
                                 Executor asyncExecutor) {
        this.workflowExecutionPort = workflowExecutionPort;
        this.executionEventPort = executionEventPort;
        this.streamPublisher = streamPublisher;
        this.asyncExecutor = asyncExecutor;
    }

    /**
     * 以流式方式执行统一工作流，并通过 WebSocket 发送事件。
     *
     * @param userInput 用户输入
     * @return 包含 sessionId 的 WorkflowResponse，用于客户端订阅
     */
    public WorkflowResponse executeWorkflowAndStreamEvents(String userInput) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return WorkflowResponse.builder()
                    .success(false)
                    .error("输入不能为空")
                    .errorCode(WorkflowErrorCodes.INPUT_INVALID)
                    .build();
        }

        String sessionId = resolveSessionId();

        final String currentSessionId = sessionId;

        WorkflowExecutionEventPort.Listener listener = event -> {
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
            return WorkflowResponse.builder()
                    .success(false)
                    .sessionId(sessionId)
                    .error("内部错误，请稍后重试")
                    .errorCode(WorkflowErrorCodes.INTERNAL_ERROR)
                    .build();
        }

        try {
            // 直接使用注入的Executor来异步执行任务
            final String finalSessionId = sessionId;
            asyncExecutor.execute(() -> executeWorkflowInternal(userInput, finalSessionId, listener));
        } catch (RejectedExecutionException e) {
            removeListenerSafely(currentSessionId, listener);
            log.error("异步任务提交失败: sessionId={}", sessionId, e);
            return WorkflowResponse.builder()
                    .success(false)
                    .sessionId(sessionId)
                    .error("任务提交失败，请稍后重试")
                    .errorCode(WorkflowErrorCodes.ASYNC_SUBMIT_REJECTED)
                    .build();
        } catch (RuntimeException e) {
            removeListenerSafely(currentSessionId, listener);
            log.error("异步任务提交异常: sessionId={}", sessionId, e);
            return WorkflowResponse.builder()
                    .success(false)
                    .sessionId(sessionId)
                    .error("任务提交异常，请稍后重试")
                    .errorCode(WorkflowErrorCodes.ASYNC_SUBMIT_EXCEPTION)
                    .build();
        }

        // 立即返回sessionId，以便客户端可以开始监听
        return WorkflowResponse.builder()
                .success(true)
                .sessionId(sessionId)
                .build();
    }

    /**
     * 执行工作流的核心同步逻辑
     * @param userInput 用户输入
     * @param sessionId 会话ID
     * @param listener 事件监听器
     */
    public void executeWorkflowInternal(String userInput, String sessionId, WorkflowExecutionEventPort.Listener listener) {
        final LocalDateTime startTime = LocalDateTime.now();

        try (MDC.MDCCloseable ignored = MDC.putCloseable(SESSION_ID_KEY, sessionId)) {
            log.info("Workflow execution started: sessionId={}", sessionId);
            executionEventPort.startWorkflowTracking(sessionId, userInput);
            executionEventPort.startExecution(sessionId, "workflow_manager", "WORKFLOW_START", userInput);
            String result = workflowExecutionPort.executeSync(userInput, sessionId);
            executionEventPort.endWorkflowTracking(sessionId, result, true);

            executionEventPort.endExecution(
                    sessionId,
                    "workflow_manager",
                    "WORKFLOW_COMPLETE",
                    result,
                    AgentExecutionEvent.ExecutionStatus.SUCCESS
            );

            LocalDateTime endTime = LocalDateTime.now();
            long executionTimeMs = ChronoUnit.MILLIS.between(startTime, endTime);
            log.info("Workflow execution completed: sessionId={}, durationMs={}", sessionId, executionTimeMs);
            sendWorkflowResult(sessionId, userInput, result, "SUCCESS", endTime, executionTimeMs);

        } catch (RuntimeException e) {
            Throwable actualError = unwrapException(e);
            String errorMessage = safeErrorMessage(actualError);
            log.error("Workflow execution failed: sessionId={}", sessionId, e);
            executionEventPort.endWorkflowTracking(sessionId, "执行出错: " + errorMessage, false);
            executionEventPort.recordError(sessionId, "workflow_manager", "WORKFLOW_EXECUTION", errorMessage);
            executionEventPort.endExecution(
                    sessionId,
                    "workflow_manager",
                    "WORKFLOW_COMPLETE",
                    "执行出错: " + errorMessage,
                    AgentExecutionEvent.ExecutionStatus.ERROR
            );

            long executionTimeMs = ChronoUnit.MILLIS.between(startTime, LocalDateTime.now());
            sendWorkflowResult(sessionId, userInput, "执行出错: " + errorMessage, "ERROR", LocalDateTime.now(), executionTimeMs);
        } finally {
            try {
                long delayMs = postExecutionDrainDelayMs();
                if (delayMs > 0) {
                    Thread.sleep(delayMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            log.debug("Workflow execution cleanup: sessionId={}", sessionId);
            removeListenerSafely(sessionId, listener);
        }
    }

    protected long postExecutionDrainDelayMs() {
        return 0L;
    }

    /**
     * 发送工作流结果。
     */
    private void sendWorkflowResult(String sessionId, String userInput, String result, 
                                   String status, LocalDateTime completedTime, long executionTimeMs) {
        WorkflowResultVO resultVO = WorkflowResultVO.builder()
                .sessionId(sessionId)
                .userInput(userInput)
                .result(result)
                .status(status)
                .completedTime(completedTime)
                .executionTime(executionTimeMs)
                .build();

        try {
            log.debug("发送工作流结果: sessionId={}", sessionId);
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

    private void removeListenerSafely(String sessionId, WorkflowExecutionEventPort.Listener listener) {
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
