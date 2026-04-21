package com.openmanus.infra.config;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class MdcInterceptorTest {
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");
    private final MdcInterceptor interceptor = new MdcInterceptor();

    @AfterEach
    void tearDown() {
        MDC.remove("sessionId");
    }

    @Test
    void shouldUseTrimmedHeaderWhenHeaderIsValid() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Session-ID", "  session-1  ");

        boolean handled = interceptor.preHandle(request, mock(HttpServletResponse.class), new Object());

        assertTrue(handled);
        assertEquals("session-1", MDC.get("sessionId"));
    }

    @Test
    void shouldGenerateSessionIdWhenHeaderContainsIllegalCharacters() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Session-ID", "bad/id");

        interceptor.preHandle(request, mock(HttpServletResponse.class), new Object());

        String sessionId = MDC.get("sessionId");
        assertNotNull(sessionId);
        assertTrue(SESSION_ID_PATTERN.matcher(sessionId).matches());
        assertFalse("bad/id".equals(sessionId));
    }

    @Test
    void shouldGenerateSessionIdWhenHeaderExceedsMaxLength() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Session-ID", "a".repeat(65));

        interceptor.preHandle(request, mock(HttpServletResponse.class), new Object());

        String sessionId = MDC.get("sessionId");
        assertNotNull(sessionId);
        assertTrue(SESSION_ID_PATTERN.matcher(sessionId).matches());
    }

    @Test
    void shouldGenerateSessionIdWhenHeaderIsBlank() {
        String normalized = MdcInterceptor.normalizeSessionId("   ");

        assertNotNull(normalized);
        assertTrue(SESSION_ID_PATTERN.matcher(normalized).matches());
    }

    @Test
    void shouldGenerateSessionIdWhenHeaderIsNull() {
        String normalized = MdcInterceptor.normalizeSessionId(null);

        assertNotNull(normalized);
        assertTrue(SESSION_ID_PATTERN.matcher(normalized).matches());
    }

    @Test
    void shouldClearMdcAfterCompletion() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Session-ID", "session-2");
        interceptor.preHandle(request, mock(HttpServletResponse.class), new Object());

        interceptor.afterCompletion(request, mock(HttpServletResponse.class), new Object(), null);

        assertNull(MDC.get("sessionId"));
    }
}
