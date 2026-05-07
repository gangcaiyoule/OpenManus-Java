package com.openmanus.domain.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal in-memory session execution guard.
 */
public class InMemorySessionExecutionGuard implements SessionExecutionGuard {

    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    @Override
    public boolean tryAcquire(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        return activeSessions.add(sessionId);
    }

    @Override
    public void release(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        activeSessions.remove(sessionId);
    }
}
