package com.openmanus.infra.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MDC Interceptor User ID Fallback Tests")
class MdcInterceptorUserIdFallbackTest {

    @Test
    @DisplayName("normalizeSessionId should prefer explicit session id")
    void normalizeSessionId_prefersExplicitSessionId() {
        String sessionId = MdcInterceptor.normalizeSessionId("session-123", "user-001", "001");

        assertThat(sessionId).isEqualTo("session-123");
    }

    @Test
    @DisplayName("normalizeSessionId should reuse user id when session id is missing")
    void normalizeSessionId_reusesUserIdWhenSessionIdMissing() {
        String sessionId = MdcInterceptor.normalizeSessionId(null, "user-001", "001");

        assertThat(sessionId).isEqualTo("user-001");
    }

    @Test
    @DisplayName("normalizeSessionId should default to 001 when ids are missing")
    void normalizeSessionId_defaultsToConfiguredUserId() {
        String sessionId = MdcInterceptor.normalizeSessionId(null, null, "001");

        assertThat(sessionId).isEqualTo("001");
    }
}
