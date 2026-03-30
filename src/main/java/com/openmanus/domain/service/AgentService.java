package com.openmanus.domain.service;

import com.openmanus.agent.workflow.UnifiedWorkflow;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * 统一工作流的 HTTP 对话服务。
 */
@Service
@Slf4j
public class AgentService {
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    private final UnifiedWorkflow unifiedWorkflow;

    @Autowired
    public AgentService(UnifiedWorkflow unifiedWorkflow) {
        this.unifiedWorkflow = unifiedWorkflow;
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
            // 同步执行
            try (MDC.MDCCloseable ignored = MDC.putCloseable("sessionId", sessionId)) {
                String response = unifiedWorkflow.executeSync(message, sessionId);
                Map<String, Object> result = new HashMap<>();
                result.put("answer", response);
                result.put("conversationId", sessionId);
                result.put("timestamp", LocalDateTime.now().toString());
                result.put("mode", "unified");
                return CompletableFuture.completedFuture(result);
            } catch (Exception e) {
                log.error("Error in sync chat execution", e);
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("error", e.getMessage());
                errorResult.put("conversationId", sessionId);
                return CompletableFuture.completedFuture(errorResult);
            }
        } else {
            // 异步执行
            try (MDC.MDCCloseable ignored = MDC.putCloseable("sessionId", sessionId)) {
                return unifiedWorkflow.execute(message, sessionId)
                .thenApply(response -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("answer", response);
                    result.put("conversationId", sessionId);
                    result.put("timestamp", LocalDateTime.now().toString());
                    result.put("mode", "unified");
                    return result;
                })
                .exceptionally(e -> {
                    log.error("Error in async chat execution", e);
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("error", e.getMessage());
                    errorResult.put("conversationId", sessionId);
                    return errorResult;
                });
            }
        }
    }

    static String normalizeSessionId(String rawConversationId) {
        if (rawConversationId == null) {
            return UUID.randomUUID().toString();
        }
        String trimmed = rawConversationId.trim();
        if (trimmed.isEmpty()) {
            return UUID.randomUUID().toString();
        }
        if (!SESSION_ID_PATTERN.matcher(trimmed).matches()) {
            return UUID.randomUUID().toString();
        }
        return trimmed;
    }
}
