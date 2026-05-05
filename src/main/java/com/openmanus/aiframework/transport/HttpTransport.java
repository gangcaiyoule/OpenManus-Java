package com.openmanus.aiframework.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openmanus.aiframework.exception.AiFrameworkException;

import java.io.IOException;
import java.io.StringReader;
import java.io.BufferedReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class HttpTransport {

    private static final long RETRY_BACKOFF_MILLIS = 250L;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HttpTransport(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public JsonNode postJson(String url,
                             JsonNode payload,
                             Map<String, String> headers,
                             int timeoutSeconds,
                             int maxRetries) {
        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new AiFrameworkException("Failed to serialize request payload", e);
        }

        int attempts = Math.max(1, maxRetries + 1);
        RuntimeException last = null;

        for (int i = 0; i < attempts; i++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body));

                if (headers != null && !headers.isEmpty()) {
                    headers.forEach(builder::header);
                }

                HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                String responseBody = response.body();
                if (status >= 200 && status < 300) {
                    return parseSuccessBody(responseBody);
                }

                if (!isRetryableResponse(status, responseBody) || i == attempts - 1) {
                    throw new AiFrameworkException("Provider request failed: status=" + status + ", body=" + responseBody);
                }
                last = new AiFrameworkException("Retryable HTTP status: " + status);
                pauseBeforeRetry(i);
            } catch (IOException e) {
                last = new AiFrameworkException("I/O error while calling provider", e);
                if (i == attempts - 1) {
                    throw last;
                }
                pauseBeforeRetry(i);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AiFrameworkException("Request interrupted", e);
            }
        }

        throw last == null ? new AiFrameworkException("Provider request failed") : last;
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
            throw new AiFrameworkException("Retry backoff interrupted", e);
        }
    }

    private JsonNode parseSuccessBody(String body) throws IOException {
        try {
            return objectMapper.readTree(body);
        } catch (IOException e) {
            if (!looksLikeSse(body)) {
                throw e;
            }
            return parseSseEnvelope(body);
        }
    }

    private boolean looksLikeSse(String body) {
        if (body == null) {
            return false;
        }
        String trimmed = body.stripLeading();
        return trimmed.startsWith("data:") || trimmed.startsWith("event:");
    }

    private JsonNode parseSseEnvelope(String body) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("_transport_format", "sse");
        ArrayNode events = root.putArray("events");

        try (BufferedReader reader = new BufferedReader(new StringReader(body))) {
            String line;
            String eventType = "message";
            StringBuilder dataBuilder = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    appendSseEvent(events, eventType, dataBuilder);
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

            appendSseEvent(events, eventType, dataBuilder);
        }

        return root;
    }

    private void appendSseEvent(ArrayNode events, String eventType, StringBuilder dataBuilder) throws IOException {
        if (dataBuilder.isEmpty()) {
            return;
        }

        ObjectNode event = events.addObject();
        String raw = dataBuilder.toString();
        event.put("eventType", "[DONE]".equals(raw) ? "[DONE]" : eventType);
        if (!"[DONE]".equals(raw)) {
            event.set("data", objectMapper.readTree(raw));
        }
    }
}
