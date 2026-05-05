package com.openmanus.agent.tool;

import com.openmanus.domain.model.AgentExecutionEvent;
import com.openmanus.domain.service.ExecutionEventPort;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class WebSearchEventSupport {

    private WebSearchEventSupport() {
    }

    static void emit(ExecutionEventPort executionEventPort,
                     String agentName,
                     AgentExecutionEvent.EventType eventType,
                     Map<String, Object> metadata) {
        if (executionEventPort == null) {
            return;
        }
        String sessionId = MDC.get("sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        if (metadata != null) {
            payload.putAll(metadata);
        }
        // 设置事件类型，确保前端能正确获取
        // 使用 event_type 字段名（前端 ExecutionEventPayload 使用 event_type）
        payload.put("event_type", eventType.name());
        payload.putIfAbsent("kind", eventType.name());

        executionEventPort.recordCustomEvent(AgentExecutionEvent.builder()
                .sessionId(sessionId)
                .eventId(UUID.randomUUID().toString())
                .agentName(agentName)
                .agentType("TOOL")
                .eventType(eventType)
                .status(AgentExecutionEvent.ExecutionStatus.SUCCESS.name())
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now())
                .metadata(payload)
                .build());
    }
}
