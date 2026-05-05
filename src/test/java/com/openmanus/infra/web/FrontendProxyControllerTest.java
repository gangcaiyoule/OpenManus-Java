package com.openmanus.infra.web;

import com.openmanus.infra.config.OpenManusProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FrontendProxyController Tests")
class FrontendProxyControllerTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("proxy should use configured frontend dev server url")
    void proxy_usesConfiguredFrontendDevServerUrl() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/workspace", exchange -> {
            byte[] body = "proxied".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", MediaType.TEXT_PLAIN_VALUE);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();

        OpenManusProperties properties = new OpenManusProperties();
        properties.getFrontend().setDevServerUrl("http://127.0.0.1:" + server.getAddress().getPort());
        FrontendProxyController controller =
                new FrontendProxyController(properties, new DefaultResourceLoader());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/workspace");
        byte[] body = controller.proxy(request).getBody();

        assertThat(body).isEqualTo("proxied".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("proxy should fall back to packaged frontend assets when dev server is unavailable")
    void proxy_fallsBackToPackagedFrontendAssets() {
        OpenManusProperties properties = new OpenManusProperties();
        properties.getFrontend().setDevServerUrl("http://127.0.0.1:9");
        FrontendProxyController controller =
                new FrontendProxyController(properties, new DefaultResourceLoader());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        var response = controller.proxy(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_HTML);
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).contains("<div id=\"root\"></div>");
    }
}
