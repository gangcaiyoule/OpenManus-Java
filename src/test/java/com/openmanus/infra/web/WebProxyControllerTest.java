package com.openmanus.infra.web;

import com.openmanus.domain.service.WebProxyResult;
import com.openmanus.domain.service.WebProxyService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebProxyControllerTest {

    @Test
    void shouldBuildProxyUrlByBase64EncodingTarget() {
        WebProxyService service = mock(WebProxyService.class);
        WebProxyController controller = new WebProxyController(service);

        String url = "https://93.184.216.34/search?q=openmanus";
        when(service.validateTargetUrl(url)).thenReturn(url);
        String expected = "/api/proxy/web?url=" + Base64.getUrlEncoder()
                .encodeToString(url.getBytes(StandardCharsets.UTF_8));

        assertEquals(expected, controller.getProxyUrl(url));
        verify(service).validateTargetUrl(url);
    }

    @Test
    void shouldReturnBadRequestForInvalidProxyUrlTarget() throws Exception {
        WebProxyService service = mock(WebProxyService.class);
        WebProxyController controller = new WebProxyController(service);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        String targetUrl = "http://127.0.0.1/admin";
        when(service.validateTargetUrl(targetUrl))
                .thenThrow(new IllegalArgumentException("Target URL host is not allowed"));

        mockMvc.perform(get("/api/proxy/url").param("target", targetUrl))
                .andExpect(status().isBadRequest());

        verify(service).validateTargetUrl(targetUrl);
        verify(service, never()).fetch(anyString());
    }

    @Test
    void shouldDecodeBase64UrlAndForwardResponse() throws Exception {
        WebProxyService service = mock(WebProxyService.class);
        WebProxyController controller = new WebProxyController(service);
        MockHttpServletResponse response = new MockHttpServletResponse();
        String targetUrl = "https://example.com/docs";
        String encodedUrl = Base64.getUrlEncoder().encodeToString(targetUrl.getBytes(StandardCharsets.UTF_8));
        WebProxyResult result = new WebProxyResult(
                200,
                "text/html; charset=utf-8",
                Map.of("Cache-Control", List.of("no-cache")),
                "<html>ok</html>".getBytes(StandardCharsets.UTF_8),
                null
        );
        when(service.fetch(targetUrl)).thenReturn(result);

        controller.proxyWeb(encodedUrl, response);

        verify(service).fetch(eq(targetUrl));
        assertEquals(200, response.getStatus());
        assertEquals("no-cache", response.getHeader("Cache-Control"));
        assertEquals("<html>ok</html>", response.getContentAsString());
    }

    @Test
    void shouldRejectRawUrlWhenInputIsNotBase64() throws Exception {
        WebProxyService service = mock(WebProxyService.class);
        WebProxyController controller = new WebProxyController(service);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.proxyWeb("example.com", response);

        assertEquals(400, response.getStatus());
        assertTrue(response.getErrorMessage().contains("base64url"));
        verify(service, never()).fetch(anyString());
    }

    @Test
    void shouldSendRedirectWhenServiceReturnsRedirect() throws Exception {
        WebProxyService service = mock(WebProxyService.class);
        WebProxyController controller = new WebProxyController(service);
        MockHttpServletResponse response = new MockHttpServletResponse();
        String targetUrl = "https://93.184.216.34";
        String encodedUrl = Base64.getUrlEncoder().encodeToString(targetUrl.getBytes(StandardCharsets.UTF_8));
        when(service.fetch(targetUrl)).thenReturn(new WebProxyResult(302, null, Map.of(), new byte[0], "/api/proxy/web?url=abc"));

        controller.proxyWeb(encodedUrl, response);

        assertEquals(302, response.getStatus());
        assertEquals("/api/proxy/web?url=abc", response.getRedirectedUrl());
    }

    @Test
    void shouldMapServiceFailureToBadGateway() throws Exception {
        WebProxyService service = mock(WebProxyService.class);
        WebProxyController controller = new WebProxyController(service);
        MockHttpServletResponse response = new MockHttpServletResponse();
        String targetUrl = "https://93.184.216.34";
        String encodedUrl = Base64.getUrlEncoder().encodeToString(targetUrl.getBytes(StandardCharsets.UTF_8));
        doThrow(new IOException("network down")).when(service).fetch(targetUrl);

        controller.proxyWeb(encodedUrl, response);

        assertEquals(502, response.getStatus());
        assertEquals("Proxy upstream request failed", response.getErrorMessage());
    }

    @Test
    void shouldIgnoreNullHeaderEntriesWhenWritingProxyResponse() throws Exception {
        WebProxyService service = mock(WebProxyService.class);
        WebProxyController controller = new WebProxyController(service);
        MockHttpServletResponse response = new MockHttpServletResponse();
        String targetUrl = "https://example.com/docs";
        String encodedUrl = Base64.getUrlEncoder().encodeToString(targetUrl.getBytes(StandardCharsets.UTF_8));
        Map<String, List<String>> headers = new LinkedHashMap<>();
        List<String> cacheControlValues = new ArrayList<>();
        cacheControlValues.add("no-cache");
        cacheControlValues.add(null);
        headers.put("Cache-Control", cacheControlValues);
        headers.put("X-Null", null);
        WebProxyResult result = new WebProxyResult(
                200,
                "text/plain",
                headers,
                "ok".getBytes(StandardCharsets.UTF_8),
                null
        );
        when(service.fetch(targetUrl)).thenReturn(result);

        controller.proxyWeb(encodedUrl, response);

        assertEquals(200, response.getStatus());
        assertEquals(List.of("no-cache"), response.getHeaders("Cache-Control"));
        assertTrue(response.getHeaders("X-Null").isEmpty());
    }

    @Test
    void shouldMapValidationFailureToBadRequest() throws Exception {
        WebProxyService service = mock(WebProxyService.class);
        WebProxyController controller = new WebProxyController(service);
        MockHttpServletResponse response = new MockHttpServletResponse();
        String targetUrl = "http://127.0.0.1/admin";
        String encodedUrl = Base64.getUrlEncoder().encodeToString(targetUrl.getBytes(StandardCharsets.UTF_8));
        doThrow(new IllegalArgumentException("Target URL host is not allowed")).when(service).fetch(targetUrl);

        controller.proxyWeb(encodedUrl, response);

        assertEquals(400, response.getStatus());
        assertTrue(response.getErrorMessage().contains("not allowed"));
    }
}
