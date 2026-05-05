package com.openmanus.aiframework.runtime;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Runtime-neutral execution event shape used by domain/transport layers.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiExecutionEvent(
        String sessionId,
        String eventId,
        String agentName,
        String agentType,
        String eventType,
        AiExecutionStatus status,
        Object input,
        Object output,
        String error,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Long duration,
        Map<String, Object> metadata
) {
}
