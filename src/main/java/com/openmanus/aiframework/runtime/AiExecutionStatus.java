package com.openmanus.aiframework.runtime;

/**
 * Runtime-neutral execution status for execution tracking.
 */
public enum AiExecutionStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED,
    ERROR,
    TIMEOUT
}
