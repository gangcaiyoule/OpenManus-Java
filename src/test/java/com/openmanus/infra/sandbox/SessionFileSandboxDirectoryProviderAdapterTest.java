package com.openmanus.infra.sandbox;

import com.openmanus.domain.service.LegacySessionMappingPolicy;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionFileSandboxDirectoryProviderAdapterTest {

    @Test
    void shouldCreateSandboxRootInsideBaseDirForValidSessionId() {
        SessionFileSandboxDirectoryProviderAdapter provider =
                new SessionFileSandboxDirectoryProviderAdapter(policy(false, 200));
        String sessionId = "safe-session-" + UUID.randomUUID().toString().replace("-", "");

        Path root = provider.getOrCreateFileSandboxRoot(sessionId);
        Path expectedBase = Paths.get(System.getProperty("java.io.tmpdir"), "openmanus", "file-sandboxes")
                .toAbsolutePath()
                .normalize();

        assertTrue(root.toAbsolutePath().normalize().startsWith(expectedBase));
        assertEquals(sessionId, root.getFileName().toString());
    }

    @Test
    void shouldMapTraversalStyleSessionIdIntoLegacyDirectoryInsideBaseDir() {
        SessionFileSandboxDirectoryProviderAdapter provider =
                new SessionFileSandboxDirectoryProviderAdapter(policy(false, 200));

        Path root = provider.getOrCreateFileSandboxRoot("../../outside");
        Path expectedBase = Paths.get(System.getProperty("java.io.tmpdir"), "openmanus", "file-sandboxes")
                .toAbsolutePath()
                .normalize();

        assertTrue(root.toAbsolutePath().normalize().startsWith(expectedBase));
        assertTrue(root.getFileName().toString().startsWith("legacy-"));
        assertEquals(71, root.getFileName().toString().length());
    }

    @Test
    void shouldRejectBlankSessionId() {
        SessionFileSandboxDirectoryProviderAdapter provider =
                new SessionFileSandboxDirectoryProviderAdapter(policy(false, 200));

        assertThrows(SecurityException.class, () -> provider.getOrCreateFileSandboxRoot("   "));
    }

    @Test
    void shouldMapLegacyConversationIdToDeterministicSafeDirectory() {
        SessionFileSandboxDirectoryProviderAdapter provider =
                new SessionFileSandboxDirectoryProviderAdapter(policy(false, 200));
        String legacy = "user.123/conversation:abc:with-long-id-xxxxxxxxxxxxxxxxxxxxxxxxxxxx";

        Path first = provider.getOrCreateFileSandboxRoot(legacy);
        Path second = provider.getOrCreateFileSandboxRoot(legacy);

        assertTrue(first.getFileName().toString().startsWith("legacy-"));
        assertEquals(71, first.getFileName().toString().length());
        assertEquals(first, second);
    }

    @Test
    void shouldBoundLegacyWarningCacheSize() {
        SessionFileSandboxDirectoryProviderAdapter provider =
                new SessionFileSandboxDirectoryProviderAdapter(policy(false, 5));

        for (int i = 0; i < 5000; i++) {
            provider.getOrCreateFileSandboxRoot("legacy.session." + i + ".with.invalid/chars");
        }

        assertTrue(provider.warnedLegacyMappingsSizeForTest() <= 2048);
    }

    private LegacySessionMappingPolicy policy(boolean warnEnabled, int warnSampleRate) {
        return new LegacySessionMappingPolicy() {
            @Override
            public boolean warnEnabled() {
                return warnEnabled;
            }

            @Override
            public int warnSampleRate() {
                return warnSampleRate;
            }
        };
    }
}
