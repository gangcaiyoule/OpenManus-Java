package com.openmanus.domain.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionSandboxManagerSecurityTest {

    @Test
    void shouldDelegateFileSandboxRootLookupToProvider() {
        SessionSandboxClient sessionSandboxClient = mock(SessionSandboxClient.class);
        SessionFileSandboxDirectoryProvider directoryProvider = mock(SessionFileSandboxDirectoryProvider.class);
        SessionSandboxManager manager = new SessionSandboxManager(sessionSandboxClient, directoryProvider);
        Path expected = Path.of("/tmp/openmanus/file-sandboxes/session-1");

        when(directoryProvider.getOrCreateFileSandboxRoot("session-1")).thenReturn(expected);

        assertEquals(expected, manager.getOrCreateFileSandboxRoot("session-1"));
        verify(directoryProvider).getOrCreateFileSandboxRoot("session-1");
    }

    @Test
    void shouldPropagateProviderFailureWhenResolvingFileSandboxRoot() {
        SessionSandboxClient sessionSandboxClient = mock(SessionSandboxClient.class);
        SessionFileSandboxDirectoryProvider directoryProvider = mock(SessionFileSandboxDirectoryProvider.class);
        SessionSandboxManager manager = new SessionSandboxManager(sessionSandboxClient, directoryProvider);

        when(directoryProvider.getOrCreateFileSandboxRoot("   "))
                .thenThrow(new SecurityException("缺少会话ID，拒绝创建文件沙盒目录"));

        assertThrows(SecurityException.class, () -> manager.getOrCreateFileSandboxRoot("   "));
    }
}
