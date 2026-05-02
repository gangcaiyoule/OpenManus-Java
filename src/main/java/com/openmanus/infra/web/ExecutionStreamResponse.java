package com.openmanus.infra.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExecutionStreamResponse {
    boolean success;

    @JsonProperty("session_id")
    String sessionId;

    String topic;
    String error;
    String errorCode;
}
