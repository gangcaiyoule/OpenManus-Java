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

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }
}
