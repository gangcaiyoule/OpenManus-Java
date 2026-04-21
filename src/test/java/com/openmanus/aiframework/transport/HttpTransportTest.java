package com.openmanus.aiframework.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openmanus.aiframework.exception.AiFrameworkException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpTransportTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldRetryOnceThenSucceed() throws Exception {
        AtomicInteger count = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat", exchange -> {
            int current = count.incrementAndGet();
            byte[] bytes = (current == 1 ? "{\"error\":\"rate limit\"}" : "{\"ok\":true}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(current == 1 ? 429 : 200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        HttpTransport transport = new HttpTransport(HttpClient.newHttpClient(), new ObjectMapper());
        ObjectNode payload = new ObjectMapper().createObjectNode().put("x", 1);
        var result = transport.postJson(url("/chat"), payload, Map.of(), 5, 1);

        assertEquals(true, result.path("ok").asBoolean());
        assertEquals(2, count.get());
    }

    @Test
    void shouldRetryVendorWrappedUpstreamFailureThenSucceed() throws Exception {
        AtomicInteger count = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat", exchange -> {
            int current = count.incrementAndGet();
            String body = current == 1
                    ? """
                    {"error":{"message":"openai_error","type":"bad_response_status_code","code":"bad_response_status_code"}}
                    """
                    : "{\"ok\":true}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(current == 1 ? 403 : 200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        HttpTransport transport = new HttpTransport(HttpClient.newHttpClient(), new ObjectMapper());
        ObjectNode payload = new ObjectMapper().createObjectNode().put("x", 1);
        var result = transport.postJson(url("/chat"), payload, Map.of(), 5, 1);

        assertEquals(true, result.path("ok").asBoolean());
        assertEquals(2, count.get());
    }

    @Test
    void shouldRetryTransientFiveHundredStatusThenSucceed() throws Exception {
        AtomicInteger count = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat", exchange -> {
            int current = count.incrementAndGet();
            byte[] bytes = (current == 1 ? "{\"error\":\"upstream unavailable\"}" : "{\"ok\":true}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(current == 1 ? 530 : 200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        HttpTransport transport = new HttpTransport(HttpClient.newHttpClient(), new ObjectMapper());
        ObjectNode payload = new ObjectMapper().createObjectNode().put("x", 1);
        var result = transport.postJson(url("/chat"), payload, Map.of(), 5, 1);

        assertEquals(true, result.path("ok").asBoolean());
        assertEquals(2, count.get());
    }

    @Test
    void shouldFailOnNonRetryableStatus() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat", exchange -> {
            byte[] bytes = "{\"error\":\"bad request\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        HttpTransport transport = new HttpTransport(HttpClient.newHttpClient(), new ObjectMapper());
        ObjectNode payload = new ObjectMapper().createObjectNode().put("x", 1);

        assertThrows(AiFrameworkException.class,
                () -> transport.postJson(url("/chat"), payload, Map.of(), 5, 1));
    }

    @Test
    void shouldAllowNullHeaders() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat", exchange -> {
            byte[] bytes = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        HttpTransport transport = new HttpTransport(HttpClient.newHttpClient(), new ObjectMapper());
        ObjectNode payload = new ObjectMapper().createObjectNode().put("x", 1);
        var result = transport.postJson(url("/chat"), payload, null, 5, 0);

        assertEquals(true, result.path("ok").asBoolean());
    }

    @Test
    void shouldWrapSuccessfulSseBodyForClientFallbackParsing() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat", exchange -> {
            String body = "data: {\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}\n\n"
                    + "data: {\"choices\":[{\"delta\":{\"content\":\"lo\"},\"finish_reason\":\"stop\"}]}\n\n"
                    + "data: [DONE]\n\n";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        HttpTransport transport = new HttpTransport(HttpClient.newHttpClient(), new ObjectMapper());
        ObjectNode payload = new ObjectMapper().createObjectNode().put("x", 1);
        var result = transport.postJson(url("/chat"), payload, Map.of(), 5, 0);

        assertEquals("sse", result.path("_transport_format").asText());
        assertEquals(3, result.path("events").size());
        assertEquals("message", result.path("events").get(0).path("eventType").asText());
        assertTrue(result.path("events").get(0).path("data").has("choices"));
        assertEquals("[DONE]", result.path("events").get(2).path("eventType").asText());
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }
}
