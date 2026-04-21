package com.openmanus.infra.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MdcInterceptorTest {

    @Test
    void shouldTrimIncomingSessionIdHeader() {
        String normalized = MdcInterceptor.normalizeSessionId("  session-1  ");
        assertEquals("session-1", normalized);
    }

    @Test
    void shouldGenerateSessionIdWhenHeaderIsBlank() {
        String normalized = MdcInterceptor.normalizeSessionId("   ");
        assertNotNull(normalized);
        assertTrue(!normalized.isBlank());
    }

    @Test
    void shouldGenerateSessionIdWhenHeaderIsNull() {
        String normalized = MdcInterceptor.normalizeSessionId(null);
        assertNotNull(normalized);
        assertTrue(!normalized.isBlank());
    }
}
