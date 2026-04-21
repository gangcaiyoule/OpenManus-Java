package com.openmanus.aiframework.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.aiframework.exception.AiFrameworkException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SseTransportTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldReadSseEvents() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/stream", exchange -> {
            String body = "data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}\n\n" +
                    "data: [DONE]\n\n";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        SseTransport transport = new SseTransport(HttpClient.newHttpClient(), new ObjectMapper());
        List<String> events = new ArrayList<>();

        transport.postSse(url("/stream"), new ObjectMapper().createObjectNode(), Map.of(), 5, 0,
                (eventType, data) -> {
                    events.add(eventType + ":" + (data == null ? "null" : data.toString()));
                    return true;
                });

        assertEquals(2, events.size());
        assertEquals("message:{\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}", events.get(0));
        assertEquals("[DONE]:null", events.get(1));
    }

    @Test
    void shouldRetryVendorWrappedUpstreamFailureThenReadSseEvents() throws Exception {
        AtomicInteger count = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/stream", exchange -> {
            int current = count.incrementAndGet();
            String body = current == 1
                    ? """
                    {"error":{"message":"openai_error","type":"bad_response_status_code","code":"bad_response_status_code"}}
                    """
                    : "data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}\n\n"
                    + "data: [DONE]\n\n";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", current == 1
                    ? "application/json"
                    : "text/event-stream");
            exchange.sendResponseHeaders(current == 1 ? 403 : 200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        SseTransport transport = new SseTransport(HttpClient.newHttpClient(), new ObjectMapper());
        List<String> events = new ArrayList<>();

        transport.postSse(url("/stream"), new ObjectMapper().createObjectNode(), Map.of(), 5, 1,
                (eventType, data) -> {
                    events.add(eventType + ":" + (data == null ? "null" : data.toString()));
                    return true;
                });

        assertEquals(2, events.size());
        assertEquals(2, count.get());
        assertEquals("message:{\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}", events.get(0));
        assertEquals("[DONE]:null", events.get(1));
    }

    @Test
    void shouldRetryTransientFiveHundredStatusThenReadSseEvents() throws Exception {
        AtomicInteger count = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/stream", exchange -> {
            int current = count.incrementAndGet();
            String body = current == 1
                    ? "{\"error\":\"upstream unavailable\"}"
                    : "data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}\n\n"
                    + "data: [DONE]\n\n";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", current == 1
                    ? "application/json"
                    : "text/event-stream");
            exchange.sendResponseHeaders(current == 1 ? 530 : 200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        SseTransport transport = new SseTransport(HttpClient.newHttpClient(), new ObjectMapper());
        List<String> events = new ArrayList<>();

        transport.postSse(url("/stream"), new ObjectMapper().createObjectNode(), Map.of(), 5, 1,
                (eventType, data) -> {
                    events.add(eventType + ":" + (data == null ? "null" : data.toString()));
                    return true;
                });

        assertEquals(2, events.size());
        assertEquals(2, count.get());
        assertEquals("message:{\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}", events.get(0));
        assertEquals("[DONE]:null", events.get(1));
    }

    @Test
    void shouldFailWhenSseChunkIsInvalidJson() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/stream", exchange -> {
            String body = "data: not-json\n\n";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        SseTransport transport = new SseTransport(HttpClient.newHttpClient(), new ObjectMapper());

        assertThrows(AiFrameworkException.class,
                () -> transport.postSse(url("/stream"), new ObjectMapper().createObjectNode(), Map.of(), 5, 0,
                        (eventType, data) -> {
                            return true;
                        }));
    }

    @Test
    void shouldPropagateEventHandlerFailureWithoutRewrappingAsParseError() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/stream", exchange -> {
            String body = "data: {\"error\":{\"message\":\"No available channel\"}}\n\n";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        SseTransport transport = new SseTransport(HttpClient.newHttpClient(), new ObjectMapper());

        AiFrameworkException error = assertThrows(AiFrameworkException.class,
                () -> transport.postSse(url("/stream"), new ObjectMapper().createObjectNode(), Map.of(), 5, 0,
                        (eventType, data) -> {
                            throw new AiFrameworkException("Provider returned error payload: message=No available channel");
                        }));

        assertEquals("Provider returned error payload: message=No available channel", error.getMessage());
    }

    @Test
    void shouldAllowNullHeaders() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/stream", exchange -> {
            String body = "data: {\"ok\":true}\n\n";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        SseTransport transport = new SseTransport(HttpClient.newHttpClient(), new ObjectMapper());
        List<String> events = new ArrayList<>();
        transport.postSse(url("/stream"), new ObjectMapper().createObjectNode(), null, 5, 0,
                (eventType, data) -> {
                    events.add(eventType + ":" + (data == null ? "null" : data.toString()));
                    return true;
                });

        assertEquals(1, events.size());
        assertEquals("message:{\"ok\":true}", events.get(0));
    }

    @Test
    void shouldRejectNullEventHandler() {
        SseTransport transport = new SseTransport(HttpClient.newHttpClient(), new ObjectMapper());
        assertThrows(NullPointerException.class,
                () -> transport.postSse(
                        "http://127.0.0.1:1/stream",
                        new ObjectMapper().createObjectNode(),
                        Map.of(),
                        5,
                        0,
                        null));
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }
}
