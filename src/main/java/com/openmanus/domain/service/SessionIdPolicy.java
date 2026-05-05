package com.openmanus.domain.service;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 会话 ID 规范化策略。
 */
public final class SessionIdPolicy {
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    private SessionIdPolicy() {
    }

    public static String normalizeOrNull(String rawSessionId) {
        if (rawSessionId == null) {
            return null;
        }
        String trimmed = rawSessionId.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (!SESSION_ID_PATTERN.matcher(trimmed).matches()) {
            return null;
        }
        return trimmed;
    }

    public static String normalizeOrGenerate(String rawSessionId) {
        String normalized = normalizeOrNull(rawSessionId);
        if (normalized != null) {
            return normalized;
        }
        return UUID.randomUUID().toString();
    }
}
