package com.openmanus.domain.model;

import lombok.Data;

/**
 * 执行请求 DTO
 * 用于接收前端传递的请求数据
 */
@Data
public class ExecutionRequest {
    /**
     * 用户输入内容
     */
    private String input;
} 
