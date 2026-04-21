package com.openmanus.aiframework.exception;

public class AiFrameworkException extends RuntimeException {

    public AiFrameworkException(String message) {
        super(message);
    }

    public AiFrameworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
