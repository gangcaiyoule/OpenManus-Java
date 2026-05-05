package com.openmanus.aiframework.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.aiframework.exception.AiFrameworkException;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public class SseTransport {

    private static final long RETRY_BACKOFF_MILLIS = 250L;

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
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
    }

    public void postSse(String url,
                        JsonNode payload,
                        Map<String, String> headers,
                        int timeoutSeconds,
                        int maxRetries,
                        SseEventHandler eventHandler) {
        Objects.requireNonNull(eventHandler, "eventHandler cannot be null");
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
            if (headers != null && !headers.isEmpty()) {
                headers.forEach(builder::header);
            }

            try {
                HttpResponse<java.io.InputStream> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if (status < 200 || status >= 300) {
                    String respBody;
                    try (java.io.InputStream responseBody = response.body()) {
                        respBody = new String(responseBody.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    if (isRetryableResponse(status, respBody) && i < attempts - 1) {
                        last = new AiFrameworkException("Retryable SSE status: " + status);
                        pauseBeforeRetry(i);
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
                pauseBeforeRetry(i);
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
            boolean hasDispatchedEvent = false;

            try {
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        if (!dispatch(eventType, dataBuilder, eventHandler)) {
                            return;
                        }
                        hasDispatchedEvent = hasDispatchedEvent || dataBuilder.length() > 0;
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
            } catch (IOException e) {
                if (!isStreamClosed(e) || (!hasDispatchedEvent && dataBuilder.length() == 0)) {
                    throw e;
                }
            }
            dispatch(eventType, dataBuilder, eventHandler);
        }
    }

    private boolean isStreamClosed(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof EOFException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("eof") || normalized.contains("closed")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean dispatch(String eventType, StringBuilder dataBuilder, SseEventHandler eventHandler) {
        if (dataBuilder.isEmpty()) {
            return true;
        }
        String raw = dataBuilder.toString();
        if ("[DONE]".equals(raw)) {
            return eventHandler.onEvent("[DONE]", null);
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new AiFrameworkException("Failed to parse SSE chunk: " + raw, e);
        }
        return eventHandler.onEvent(eventType, node);
    }

    private boolean isRetryableStatus(int status) {
        return status == 429 || (status >= 500 && status < 600);
    }

    private boolean isRetryableResponse(int status, String body) {
        if (isRetryableStatus(status)) {
            return true;
        }
        if (status != 403 || body == null || body.isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode error = root.path("error");
            if (!error.isObject() || error.isEmpty()) {
                return false;
            }
            String type = error.path("type").asText("");
            String code = error.path("code").asText("");
            return "bad_response_status_code".equals(type) || "bad_response_status_code".equals(code);
        } catch (IOException ignored) {
            return false;
        }
    }

    private void pauseBeforeRetry(int attemptIndex) {
        try {
            Thread.sleep(RETRY_BACKOFF_MILLIS * (attemptIndex + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiFrameworkException("SSE retry backoff interrupted", e);
        }
    }
}
