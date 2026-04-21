package com.openmanus.infra.web;

import com.openmanus.domain.model.WorkflowErrorCodes;
import com.openmanus.domain.model.WorkflowRequest;
import com.openmanus.domain.model.WorkflowResponse;
import com.openmanus.domain.service.AgentService;
import com.openmanus.domain.service.SessionIdPolicy;
import com.openmanus.domain.service.SessionSandboxManager;
import com.openmanus.domain.service.WorkflowStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for interacting with Agent capabilities.
 * 提供统一工作流的不同交互入口（HTTP对话 / WebSocket流式）。
 */
@RestController
@RequestMapping("/api/agent")
@Tag(name = "Agent API", description = "Web API interface for intelligent agent")
public class AgentController {
    private static final String ERROR_EMPTY_INPUT = "输入不能为空";
    private static final String ERROR_ASYNC_SUBMIT_FAILED = "任务提交失败，请稍后重试";
    private static final String ERROR_ASYNC_SUBMIT_EXCEPTION = "任务提交异常，请稍后重试";
    private static final String EXECUTION_TOPIC_PREFIX = "/topic/executions/";

    private final AgentService agentService;
    private final WorkflowStreamService workflowStreamService;
    private final SessionSandboxManager sessionSandboxManager;
    
    public AgentController(
            AgentService agentService, 
            WorkflowStreamService workflowStreamService,
            SessionSandboxManager sessionSandboxManager) {
        this.agentService = agentService;
        this.workflowStreamService = workflowStreamService;
        this.sessionSandboxManager = sessionSandboxManager;
    }
    /**
     * 统一工作流（HTTP 对话入口）
     *
     * @param payload 包含"message"和可选的"conversationId"
     * @param stateful 是否保持会话状态
     * @param sync 是否同步执行
     * @return Agent的响应
     */
    @PostMapping("/chat")
    @Operation(
        summary = "Unified Workflow (HTTP Chat)",
        description = "Single-agent unified workflow over HTTP. Supports both stateless and stateful conversation."
    )
    public CompletableFuture<ResponseEntity<Map<String, Object>>> chat(
            @RequestBody Map<String, String> payload,
            @RequestParam(defaultValue = "false") boolean stateful,
            @RequestParam(defaultValue = "false") boolean sync) {

        String message = payload.get("message");
        String rawConversationId = payload.get("conversationId");
        String conversationId = stateful ? normalizeConversationId(rawConversationId) : null;
        if (message == null || message.trim().isEmpty()) {
            return CompletableFuture.completedFuture(ResponseEntity.badRequest()
                    .body(buildInputInvalidError("message不能为空", conversationId)));
        }

        if (stateful && payload.containsKey("conversationId") && conversationId == null) {
            return CompletableFuture.completedFuture(ResponseEntity.badRequest()
                    .body(buildInputInvalidError("conversationId非法", null)));
        }

        try {
            return agentService.chat(message, conversationId, sync)
                    .handle((result, throwable) -> throwable == null
                            ? toChatResponse(result)
                            : buildChatInternalErrorResponse(conversationId));
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(buildChatInternalErrorResponse(conversationId));
        }
    }

    /**
     * 统一工作流（流式入口）
     *
     * @param workflowRequest 包含用户输入的请求
     * @return 立即返回一个包含sessionId的响应，用于WebSocket订阅
     */
    @PostMapping("/workflow-stream")
    @Operation(
            summary = "Unified Workflow (Streaming)",
            description = "Runs the same single-agent workflow and returns a session ID for WebSocket streaming."
    )
    public ResponseEntity<WorkflowStreamResponse> workflowStream(
            @RequestBody WorkflowRequest workflowRequest) {
        String userInput = workflowRequest.getInput();
        WorkflowResponse serviceResult = workflowStreamService.executeWorkflowAndStreamEvents(userInput);

        if (!serviceResult.isSuccess()) {
            HttpStatus status = resolveErrorStatus(serviceResult.getErrorCode(), serviceResult.getError());
            return ResponseEntity.status(status).body(toStreamResponse(serviceResult));
        }

        // 从服务层获取sessionId
        String sessionId = serviceResult.getSessionId();
        if (sessionId == null) {
            // 如果没有sessionId，返回一个错误
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(WorkflowStreamResponse.builder()
                    .success(false)
                    .error("无法启动工作流：未生成会话ID")
                    .errorCode(WorkflowErrorCodes.INTERNAL_ERROR)
                    .build());
        }
        return ResponseEntity.ok(toStreamResponse(serviceResult));
    }

    private WorkflowStreamResponse toStreamResponse(WorkflowResponse serviceResult) {
        WorkflowStreamResponse.WorkflowStreamResponseBuilder builder = WorkflowStreamResponse.builder()
                .success(serviceResult.isSuccess())
                .sessionId(serviceResult.getSessionId())
                .error(serviceResult.getError())
                .errorCode(serviceResult.getErrorCode());
        if (serviceResult.isSuccess() && serviceResult.getSessionId() != null) {
            builder.topic(EXECUTION_TOPIC_PREFIX + serviceResult.getSessionId());
        }
        return builder.build();
    }

    private HttpStatus resolveErrorStatus(String errorCode, String error) {
        if (WorkflowErrorCodes.INPUT_INVALID.equals(errorCode)) {
            return HttpStatus.BAD_REQUEST;
        }
        if (WorkflowErrorCodes.ASYNC_SUBMIT_REJECTED.equals(errorCode)
                || WorkflowErrorCodes.ASYNC_SUBMIT_EXCEPTION.equals(errorCode)) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (WorkflowErrorCodes.INTERNAL_ERROR.equals(errorCode)) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }

        // Backward-compatible fallback for payloads without errorCode.
        if (ERROR_EMPTY_INPUT.equals(error)) {
            return HttpStatus.BAD_REQUEST;
        }
        if (ERROR_ASYNC_SUBMIT_FAILED.equals(error) || ERROR_ASYNC_SUBMIT_EXCEPTION.equals(error)) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private ResponseEntity<Map<String, Object>> toChatResponse(Map<String, Object> result) {
        if (result == null) {
            return buildChatInternalErrorResponse(null);
        }
        Object error = result.get("error");
        if (!(error instanceof String errorMessage) || errorMessage.isBlank()) {
            return ResponseEntity.ok(result);
        }

        Map<String, Object> response = new HashMap<>(result);
        String errorCode = stringValue(result.get("errorCode"));
        if (errorCode == null) {
            errorCode = inferChatErrorCode(errorMessage);
            response.put("errorCode", errorCode);
        }
        return ResponseEntity.status(resolveErrorStatus(errorCode, errorMessage)).body(response);
    }

    private ResponseEntity<Map<String, Object>> buildChatInternalErrorResponse(String conversationId) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", "内部错误，请稍后重试");
        error.put("errorCode", WorkflowErrorCodes.INTERNAL_ERROR);
        if (conversationId != null && !conversationId.isBlank()) {
            error.put("conversationId", conversationId);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    private Map<String, Object> buildInputInvalidError(String errorMessage, String conversationId) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", errorMessage);
        error.put("errorCode", WorkflowErrorCodes.INPUT_INVALID);
        if (conversationId != null && !conversationId.isBlank()) {
            error.put("conversationId", conversationId);
        }
        return error;
    }

    private String inferChatErrorCode(String errorMessage) {
        if ("message不能为空".equals(errorMessage)) {
            return WorkflowErrorCodes.INPUT_INVALID;
        }
        return WorkflowErrorCodes.INTERNAL_ERROR;
    }

    private String stringValue(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return null;
    }

    private static String normalizeConversationId(String rawConversationId) {
        return SessionIdPolicy.normalizeOrNull(rawConversationId);
    }

    /**
     * 查询会话信息（包括沙箱状态）
     * 
     * @param sessionId 会话 ID
     * @return 会话信息，包含沙箱 VNC URL（如果已创建）
     */
    @GetMapping("/session/{sessionId}")
    @Operation(
        summary = "Get Session Info",
        description = "Returns session information including VNC sandbox URL if available. " +
                      "Frontend can use this to poll for sandbox status and display the browser workspace."
    )
    public ResponseEntity<Map<String, Object>> getSessionInfo(@PathVariable String sessionId) {
        String normalizedSessionId = normalizeConversationId(sessionId);
        if (normalizedSessionId == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "sessionId非法");
            error.put("errorCode", WorkflowErrorCodes.INPUT_INVALID);
            return ResponseEntity.badRequest().body(error);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", normalizedSessionId);
        
        // 查询沙箱信息
        sessionSandboxManager.getSandboxInfo(normalizedSessionId).ifPresent(sandboxInfo -> {
            response.put("sandboxVncUrl", sandboxInfo.getVncUrl());
            response.put("sandboxStatus", sandboxInfo.getStatus().toString());
            response.put("sandboxCreatedAt", sandboxInfo.getCreatedAt());
            response.put("sandboxAvailable", sandboxInfo.isAvailable());
        });
        
        return ResponseEntity.ok(response);
    }
} 
