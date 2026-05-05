package com.openmanus.infra.web;

import com.openmanus.domain.service.WebPreviewDiagnostic;
import com.openmanus.domain.service.WebProxyResult;
import com.openmanus.domain.service.WebProxyService;
import com.openmanus.infra.config.OpenManusProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("WebProxyController Tests")
class WebProxyControllerTest {

    @Test
    @DisplayName("inspect should report proxy-disabled instead of 404 fallback")
    void inspect_returnsProxyDisabledDiagnostic() {
        WebProxyService webProxyService = mock(WebProxyService.class);
        OpenManusProperties properties = new OpenManusProperties();
        properties.getWebProxy().setEnabled(false);
        when(webProxyService.inspect("https://example.com", false)).thenReturn(new WebPreviewDiagnostic(
                false,
                "https://example.com",
                "/api/proxy/web?url=abc",
                "proxy-disabled",
                "proxy-disabled",
                "网页代理未启用，无法进行站内预览",
                "external",
                null,
                null,
                false,
                true
        ));
        WebProxyController controller = new WebProxyController(webProxyService, properties);

        WebPreviewDiagnostic diagnostic = controller.inspect("https://example.com");

        assertThat(diagnostic.state()).isEqualTo("proxy-disabled");
        assertThat(diagnostic.fallbackToVnc()).isTrue();
    }

    @Test
    @DisplayName("proxyWeb should render diagnostic html when proxy is disabled")
    void proxyWeb_rendersDiagnosticHtmlWhenDisabled() throws Exception {
        WebProxyService webProxyService = mock(WebProxyService.class);
        OpenManusProperties properties = new OpenManusProperties();
        properties.getWebProxy().setEnabled(false);
        WebProxyController controller = new WebProxyController(webProxyService, properties);
        MockHttpServletResponse response = new MockHttpServletResponse();
        String encoded = java.util.Base64.getUrlEncoder()
                .encodeToString("https://example.com".getBytes(StandardCharsets.UTF_8));

        controller.proxyWeb(encoded, response);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("X-OpenManus-Proxy-Status")).isEqualTo("proxy-disabled");
        assertThat(response.getContentAsString()).contains("网页代理未启用");
    }

    @Test
    @DisplayName("getProxyUrl should reject when proxy is disabled")
    void getProxyUrl_rejectsWhenDisabled() {
        WebProxyService webProxyService = mock(WebProxyService.class);
        OpenManusProperties properties = new OpenManusProperties();
        properties.getWebProxy().setEnabled(false);
        WebProxyController controller = new WebProxyController(webProxyService, properties);

        assertThatThrownBy(() -> controller.getProxyUrl("https://example.com"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("503 SERVICE_UNAVAILABLE");
    }

    @Test
    @DisplayName("proxyWeb should stream upstream html when proxy is enabled")
    void proxyWeb_streamsHtmlWhenEnabled() throws Exception {
        WebProxyService webProxyService = mock(WebProxyService.class);
        OpenManusProperties properties = new OpenManusProperties();
        properties.getWebProxy().setEnabled(true);
        when(webProxyService.fetch("https://example.com")).thenReturn(new WebProxyResult(
                200,
                "text/html; charset=utf-8",
                Map.of("Cache-Control", List.of("no-store")),
                "<html><body>ok</body></html>".getBytes(StandardCharsets.UTF_8),
                null
        ));
        WebProxyController controller = new WebProxyController(webProxyService, properties);
        MockHttpServletResponse response = new MockHttpServletResponse();
        String encoded = java.util.Base64.getUrlEncoder()
                .encodeToString("https://example.com".getBytes(StandardCharsets.UTF_8));

        controller.proxyWeb(encoded, response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("ok");
    }
}
