package com.openmanus.infra.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowStreamResponse {
    boolean success;
    String sessionId;
    String topic;
    String error;
    String errorCode;
}
