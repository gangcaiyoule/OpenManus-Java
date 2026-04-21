package com.openmanus.domain.service;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionIdPolicyTest {
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    @Test
    void shouldReturnTrimmedSessionIdWhenValid() {
        assertEquals("session-1", SessionIdPolicy.normalizeOrNull("  session-1  "));
    }

    @Test
    void shouldReturnNullWhenSessionIdContainsIllegalCharacters() {
        assertNull(SessionIdPolicy.normalizeOrNull("bad/id"));
    }

    @Test
    void shouldReturnNullWhenSessionIdExceedsMaxLength() {
        assertNull(SessionIdPolicy.normalizeOrNull("a".repeat(65)));
    }

    @Test
    void shouldReturnNullWhenSessionIdIsBlankOrNull() {
        assertNull(SessionIdPolicy.normalizeOrNull("   "));
        assertNull(SessionIdPolicy.normalizeOrNull(null));
    }

    @Test
    void shouldAcceptSessionIdAtMaxLengthBoundary() {
        String sessionId = "a".repeat(64);

        assertEquals(sessionId, SessionIdPolicy.normalizeOrNull(sessionId));
    }

    @Test
    void shouldGenerateValidSessionIdWhenRawValueIsInvalid() {
        String generated = SessionIdPolicy.normalizeOrGenerate("bad/id");

        assertNotNull(generated);
        assertTrue(SESSION_ID_PATTERN.matcher(generated).matches());
    }
}
