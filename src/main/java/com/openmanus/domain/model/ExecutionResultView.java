package com.openmanus.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 执行结果视图对象
 * 用于传递执行链路的最终结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExecutionResultView {

    /**
     * 会话ID
     */
    @JsonProperty("session_id")
    private String sessionId;

    /**
     * 消息类型
     */
    private String messageType = "EXECUTION_RESULT";

    /**
     * 用户输入
     */
    @JsonProperty("user_input")
    private String userInput;

    /**
     * 最终结果
     */
    private String result;

    /**
     * 执行状态
     */
    private String status;

    /**
     * 完成时间
     */
    @JsonProperty("completed_time")
    private LocalDateTime completedTime;

    /**
     * 执行时长(毫秒)
     */
    @JsonProperty("execution_time")
    private Long executionTime;
} 
