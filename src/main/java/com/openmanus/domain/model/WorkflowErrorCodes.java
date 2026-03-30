package com.openmanus.domain.model;

public final class WorkflowErrorCodes {

    private WorkflowErrorCodes() {
    }

    public static final String INPUT_INVALID = "INPUT_INVALID";
    public static final String ASYNC_SUBMIT_REJECTED = "ASYNC_SUBMIT_REJECTED";
    public static final String ASYNC_SUBMIT_EXCEPTION = "ASYNC_SUBMIT_EXCEPTION";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    // Fallback-only code for controller status mapping when upstream returns an unknown business error code.
    // It is not a stable output of WorkflowStreamService in normal execution paths.
    public static final String UNKNOWN_ERROR = "UNKNOWN_ERROR";
}
