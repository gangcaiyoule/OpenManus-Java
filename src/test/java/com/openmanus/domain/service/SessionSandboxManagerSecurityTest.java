package com.openmanus.domain.service;

import com.openmanus.infra.sandbox.VncSandboxClient;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SessionSandboxManagerSecurityTest {

    @Test
    void shouldCreateSandboxRootInsideBaseDirForValidSessionId() {
        SessionSandboxManager manager = new SessionSandboxManager(mock(VncSandboxClient.class));
        String sessionId = "safe-session-" + UUID.randomUUID().toString().replace("-", "");

        Path root = manager.getOrCreateFileSandboxRoot(sessionId);
        Path expectedBase = Paths.get(System.getProperty("java.io.tmpdir"), "openmanus", "file-sandboxes")
                .toAbsolutePath()
                .normalize();

        assertTrue(root.toAbsolutePath().normalize().startsWith(expectedBase));
        assertTrue(root.getFileName().toString().equals(sessionId));
    }

    @Test
    void shouldRejectTraversalStyleSessionId() {
        SessionSandboxManager manager = new SessionSandboxManager(mock(VncSandboxClient.class));
        Path root = manager.getOrCreateFileSandboxRoot("../../outside");
        Path expectedBase = Paths.get(System.getProperty("java.io.tmpdir"), "openmanus", "file-sandboxes")
                .toAbsolutePath()
                .normalize();
        assertTrue(root.toAbsolutePath().normalize().startsWith(expectedBase));
        assertTrue(root.getFileName().toString().startsWith("legacy-"));
        assertTrue(root.getFileName().toString().length() == 71);
    }

    @Test
    void shouldRejectBlankSessionId() {
        SessionSandboxManager manager = new SessionSandboxManager(mock(VncSandboxClient.class));
        assertThrows(SecurityException.class, () -> manager.getOrCreateFileSandboxRoot("   "));
    }

    @Test
    void shouldMapLegacyConversationIdToDeterministicSafeDirectory() {
        SessionSandboxManager manager = new SessionSandboxManager(mock(VncSandboxClient.class));
        String legacy = "user.123/conversation:abc:with-long-id-xxxxxxxxxxxxxxxxxxxxxxxxxxxx";

        Path first = manager.getOrCreateFileSandboxRoot(legacy);
        Path second = manager.getOrCreateFileSandboxRoot(legacy);

        assertTrue(first.getFileName().toString().startsWith("legacy-"));
        assertTrue(first.getFileName().toString().length() == 71);
        assertTrue(first.equals(second));
    }

    @Test
    void shouldBoundLegacyWarningCacheSize() {
        SessionSandboxManager manager = new SessionSandboxManager(mock(VncSandboxClient.class));

        for (int i = 0; i < 5000; i++) {
            manager.getOrCreateFileSandboxRoot("legacy.session." + i + ".with.invalid/chars");
        }

        assertTrue(manager.warnedLegacyMappingsSizeForTest() <= 2048);
    }

    @Test
    void shouldFallbackToDefaultWhenLegacyWarnSampleRateIsInvalid() {
        assertEquals(200, SessionSandboxManager.resolveLegacyMappingWarnSampleRate(null));
        assertEquals(200, SessionSandboxManager.resolveLegacyMappingWarnSampleRate(""));
        assertEquals(200, SessionSandboxManager.resolveLegacyMappingWarnSampleRate("abc"));
        assertEquals(200, SessionSandboxManager.resolveLegacyMappingWarnSampleRate("0"));
        assertEquals(200, SessionSandboxManager.resolveLegacyMappingWarnSampleRate("-3"));
        assertEquals(5, SessionSandboxManager.resolveLegacyMappingWarnSampleRate("5"));
        assertEquals(200, SessionSandboxManager.resolveLegacyMappingWarnSampleRate("200"));
        assertTrue(SessionSandboxManager.isLegacyMappingWarnSampleRateInvalid("abc"));
        assertTrue(SessionSandboxManager.isLegacyMappingWarnSampleRateInvalid("0"));
        assertTrue(SessionSandboxManager.isLegacyMappingWarnSampleRateInvalid("-3"));
        assertTrue(!SessionSandboxManager.isLegacyMappingWarnSampleRateInvalid("200"));
    }
}
