package com.openmanus.infra.monitoring;

import com.openmanus.domain.model.AgentExecutionEvent;
import com.openmanus.domain.model.ExecutionResultView;
import com.openmanus.domain.service.ExecutionStreamPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class WebSocketExecutionStreamPublisher implements ExecutionStreamPublisher {

    private static final String EXECUTION_TOPIC_PREFIX = "/topic/executions/";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketExecutionStreamPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void publishEvent(String sessionId, AgentExecutionEvent event) {
        messagingTemplate.convertAndSend(EXECUTION_TOPIC_PREFIX + sessionId, event);
        publishThoughtLog(sessionId, event);
    }

    @Override
    public void publishResult(String sessionId, ExecutionResultView result) {
        messagingTemplate.convertAndSend(EXECUTION_TOPIC_PREFIX + sessionId + "/result", result);
    }

    private void publishThoughtLog(String sessionId, AgentExecutionEvent event) {
        String message = formatThoughtMessage(event);
        if (message == null || message.isBlank()) {
            return;
        }
        messagingTemplate.convertAndSend(EXECUTION_TOPIC_PREFIX + sessionId + "/logs", logPayload(sessionId, message));
    }

    private String formatThoughtMessage(AgentExecutionEvent event) {
        if (event == null || event.getEventType() == null) {
            return null;
        }
        return switch (event.getEventType()) {
            case AGENT_START -> formatAgentStart(event);
            case AGENT_END -> formatAgentEnd(event);
            case EXECUTION_START, EXECUTION_START_MARKER -> formatExecutionStart(event);
            case EXECUTION_END, EXECUTION_END_MARKER -> formatExecutionComplete(event);
            case LLM_REQUEST -> formatLlmRequest(event);
            case LLM_RESPONSE -> formatLlmResponse(event);
            case ERROR -> formatError(event);
            case TOOL_CALL_START, TOOL_CALL_END -> formatToolEvent(event);
            default -> null;
        };
    }

    private String formatAgentStart(AgentExecutionEvent event) {
        Object iteration = readMetadata(event, "iteration");
        if (iteration != null) {
            return "执行迭代\n第 " + iteration + " 轮";
        }
        if (isExecutionLifecycleEvent(event, "EXECUTION_START")) {
            return formatExecutionStart(event);
        }
        return "执行开始\n" + stringify(event.getInput());
    }

    private String formatAgentEnd(AgentExecutionEvent event) {
        if (isExecutionLifecycleEvent(event, "EXECUTION_COMPLETE")) {
            return formatExecutionComplete(event);
        }
        String output = stringify(event.getOutput());
        if (output.isBlank()) {
            return "执行结束";
        }
        return "执行结束\n" + output;
    }

    private String formatExecutionStart(AgentExecutionEvent event) {
        String input = stringify(event.getInput());
        if (input.isBlank()) {
            return "执行开始";
        }
        return "执行开始\n" + input;
    }

    private String formatExecutionComplete(AgentExecutionEvent event) {
        String output = stringify(event.getOutput());
        String status = stringify(event.getStatus());
        if (output.isBlank()) {
            return status.isBlank() ? "执行完成" : "执行完成\n状态: " + status;
        }
        return "执行完成\n" + output;
    }

    private String formatLlmRequest(AgentExecutionEvent event) {
        String input = stringify(event.getInput());
        if (input.isBlank()) {
            input = stringify(event.getMetadata());
        }
        if (input.isBlank()) {
            return "模型思考中";
        }
        return "模型请求\n" + input;
    }

    private String formatLlmResponse(AgentExecutionEvent event) {
        String text = stringify(event.getOutput());
        if (text.isBlank()) {
            return null;
        }
        return "模型输出\n" + text;
    }

    private String formatToolEvent(AgentExecutionEvent event) {
        StringBuilder builder = new StringBuilder();
        builder.append("工具事件: ")
                .append(event.getAgentName() == null ? "" : event.getAgentName())
                .append(" [")
                .append(event.getEventType().name())
                .append("]");

        String input = stringify(event.getInput());
        if (!input.isBlank()) {
            builder.append("\n参数:\n").append(input);
        }

        String output = stringify(event.getOutput());
        if (!output.isBlank()) {
            builder.append("\n输出:\n").append(output);
        }
        return builder.toString();
    }

    private String formatError(AgentExecutionEvent event) {
        String error = stringify(event.getError());
        if (error.isBlank()) {
            error = stringify(event.getOutput());
        }
        if (error.isBlank()) {
            return "执行异常";
        }
        return "执行异常\n" + error;
    }

    private Map<String, Object> logPayload(String sessionId, String message) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("timestamp", TIME_FORMATTER.format(LocalDateTime.now()));
        payload.put("level", "INFO");
        payload.put("logger", "workflow-thought");
        payload.put("message", message);
        return payload;
    }

    private String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text.trim();
        }
        return String.valueOf(value).trim();
    }

    private Object readMetadata(AgentExecutionEvent event, String key) {
        Map<String, Object> metadata = event.getMetadata();
        if (metadata == null || key == null || key.isBlank()) {
            return null;
        }
        return metadata.get(key);
    }

    private boolean isExecutionLifecycleEvent(AgentExecutionEvent event, String expectedAgentType) {
        return event != null
                && expectedAgentType.equals(event.getAgentType());
    }
}
