package com.openmanus.aiframework.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.aiframework.exception.AiFrameworkException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public class SseTransport {

    @FunctionalInterface
    public interface SseEventHandler {
        /**
         * @return true to continue reading stream, false to stop reading immediately
         */
        boolean onEvent(String eventType, JsonNode data);
    }

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public SseTransport(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public void postSse(String url,
                        JsonNode payload,
                        Map<String, String> headers,
                        int timeoutSeconds,
                        int maxRetries,
                        SseEventHandler eventHandler) {
        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new AiFrameworkException("Failed to serialize SSE payload", e);
        }

        int attempts = Math.max(1, maxRetries + 1);
        RuntimeException last = null;

        for (int i = 0; i < attempts; i++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                    .header("Accept", "text/event-stream")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            headers.forEach(builder::header);

            try {
                HttpResponse<java.io.InputStream> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if (status < 200 || status >= 300) {
                    String respBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                    if (isRetryableStatus(status) && i < attempts - 1) {
                        last = new AiFrameworkException("Retryable SSE status: " + status);
                        continue;
                    }
                    throw new AiFrameworkException("SSE provider request failed: status=" + status + ", body=" + respBody);
                }

                readEvents(response, eventHandler);
                return;
            } catch (IOException e) {
                last = new AiFrameworkException("I/O error while reading SSE stream", e);
                if (i == attempts - 1) {
                    throw last;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AiFrameworkException("SSE request interrupted", e);
            }
        }

        throw last == null ? new AiFrameworkException("SSE provider request failed") : last;
    }

    private void readEvents(HttpResponse<java.io.InputStream> response, SseEventHandler eventHandler) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            String eventType = "message";
            StringBuilder dataBuilder = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    if (!dispatch(eventType, dataBuilder, eventHandler)) {
                        return;
                    }
                    eventType = "message";
                    dataBuilder.setLength(0);
                    continue;
                }

                if (line.startsWith("event:")) {
                    eventType = line.substring("event:".length()).trim();
                } else if (line.startsWith("data:")) {
                    if (dataBuilder.length() > 0) {
                        dataBuilder.append('\n');
                    }
                    dataBuilder.append(line.substring("data:".length()).trim());
                }
            }
            dispatch(eventType, dataBuilder, eventHandler);
        }
    }

    private boolean dispatch(String eventType, StringBuilder dataBuilder, SseEventHandler eventHandler) {
        if (dataBuilder.isEmpty()) {
            return true;
        }
        String raw = dataBuilder.toString();
        if ("[DONE]".equals(raw)) {
            return eventHandler.onEvent("[DONE]", null);
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            return eventHandler.onEvent(eventType, node);
        } catch (Exception e) {
            throw new AiFrameworkException("Failed to parse SSE chunk: " + raw, e);
        }
    }

    private boolean isRetryableStatus(int status) {
        return status == 429 || (status >= 500 && status < 600);
    }
}
