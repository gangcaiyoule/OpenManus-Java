package com.openmanus.domain.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebProxyServiceTest {

    @Test
    void shouldDelegateFetchToPort() throws Exception {
        WebProxyFetchPort port = mock(WebProxyFetchPort.class);
        WebProxyService service = new WebProxyService(port);
        WebProxyResult expected = new WebProxyResult(
                200,
                "text/plain",
                Map.of(),
                "ok".getBytes(StandardCharsets.UTF_8),
                null
        );
        when(port.fetch("https://example.com")).thenReturn(expected);

        WebProxyResult result = service.fetch("https://example.com");

        assertEquals(expected, result);
        verify(port).fetch("https://example.com");
    }

    @Test
    void shouldPropagatePortFailure() throws Exception {
        WebProxyFetchPort port = mock(WebProxyFetchPort.class);
        WebProxyService service = new WebProxyService(port);
        when(port.fetch("https://example.com")).thenThrow(new IOException("network down"));

        IOException exception = assertThrows(IOException.class, () -> service.fetch("https://example.com"));

        assertEquals("network down", exception.getMessage());
    }

    @Test
    void shouldDelegateValidationToPort() {
        WebProxyFetchPort port = mock(WebProxyFetchPort.class);
        WebProxyService service = new WebProxyService(port);
        when(port.normalizeTargetUrl("https://example.com/path"))
                .thenReturn("https://example.com/path");

        String normalized = service.validateTargetUrl("https://example.com/path");

        assertEquals("https://example.com/path", normalized);
        verify(port).normalizeTargetUrl("https://example.com/path");
    }

    @Test
    void shouldPropagateValidationFailureFromPort() {
        WebProxyFetchPort port = mock(WebProxyFetchPort.class);
        WebProxyService service = new WebProxyService(port);
        when(port.normalizeTargetUrl("ftp://example.com/file"))
                .thenThrow(new IllegalArgumentException("Target URL must use http or https"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.validateTargetUrl("ftp://example.com/file"));

        assertEquals("Target URL must use http or https", exception.getMessage());
        verify(port).normalizeTargetUrl("ftp://example.com/file");
    }
}
