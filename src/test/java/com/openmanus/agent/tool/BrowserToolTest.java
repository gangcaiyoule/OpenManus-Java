package com.openmanus.agent.tool;

import com.openmanus.aiframework.runtime.AiProxyConfig;
import com.openmanus.aiframework.runtime.AiSearchConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserToolTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void searchWebShouldFallbackWhenApiKeyIsMissing() {
        BrowserTool browserTool = new BrowserTool(null, searchConfig("", "http://unused"), disabledProxyConfig());

        String result = browserTool.searchWeb("Java programming");

        assertTrue(result.contains("搜索 API 未配置"));
        assertTrue(result.contains("https://www.google.com/search?q=Java+programming"));
    }

    @Test
    void browseWebShouldReturnPageContentForReachableUrl() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> writeResponse(exchange, 200, "hello from browser tool"));
        server.start();

        BrowserTool browserTool = new BrowserTool(null, searchConfig("", "http://unused"), disabledProxyConfig());

        String result = browserTool.browseWeb("http://127.0.0.1:" + server.getAddress().getPort() + "/");

        assertTrue(result.startsWith("网页内容:\n"));
        assertTrue(result.contains("hello from browser tool"));
    }

    @Test
    void browseWebShouldReturnHttpStatusWhenServerRejectsRequest() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> writeResponse(exchange, 404, "missing"));
        server.start();

        BrowserTool browserTool = new BrowserTool(null, searchConfig("", "http://unused"), disabledProxyConfig());

        String result = browserTool.browseWeb("http://127.0.0.1:" + server.getAddress().getPort());

        assertEquals("访问失败，HTTP状态码: 404", result);
    }

    @Test
    void browseWebShouldTruncateOversizedContentAtBoundary() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        String payload = "A".repeat(10032);
        server.createContext("/", exchange -> writeResponse(exchange, 200, payload));
        server.start();

        BrowserTool browserTool = new BrowserTool(null, searchConfig("", "http://unused"), disabledProxyConfig());

        String result = browserTool.browseWeb("http://127.0.0.1:" + server.getAddress().getPort() + "/");

        assertTrue(result.startsWith("网页内容:\n"));
        assertTrue(result.endsWith("\n... (内容已截断)"));
        assertFalse(result.contains("A".repeat(10001)));
    }

    @Test
    void browseWebShouldReturnReadableFailureForUnreachableEndpoint() {
        BrowserTool browserTool = new BrowserTool(null, searchConfig("", "http://unused"), disabledProxyConfig());

        String result = browserTool.browseWeb("http://127.0.0.1:1");

        assertTrue(result.startsWith("访问网页失败: "));
    }

    private static AiSearchConfig searchConfig(String apiKey, String endpoint) {
        return new AiSearchConfig() {
            @Override
            public String apiKey() {
                return apiKey;
            }

            @Override
            public int maxResults() {
                return 10;
            }

            @Override
            public String serperEndpoint() {
                return endpoint;
            }
        };
    }

    private static AiProxyConfig disabledProxyConfig() {
        return new AiProxyConfig() {
            @Override
            public boolean enabled() {
                return false;
            }

            @Override
            public String httpProxy() {
                return "";
            }

            @Override
            public String httpsProxy() {
                return "";
            }
        };
    }

    private static void writeResponse(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        } finally {
            exchange.close();
        }
    }
}
