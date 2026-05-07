package com.openmanus.domain.service;

/**
 * Guards one active execution per session.
 */
public interface SessionExecutionGuard {

    boolean tryAcquire(String sessionId);

    void release(String sessionId);
}
