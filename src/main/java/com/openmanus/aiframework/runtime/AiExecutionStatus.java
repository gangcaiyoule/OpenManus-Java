package com.openmanus.aiframework.runtime;

/**
 * Runtime-neutral execution status for workflow tracking.
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
