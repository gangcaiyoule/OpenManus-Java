package com.openmanus.aiframework.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.aiframework.exception.AiFrameworkException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class HttpTransport {

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

                headers.forEach(builder::header);

                HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    return objectMapper.readTree(response.body());
                }

                if (!isRetryableStatus(status) || i == attempts - 1) {
                    throw new AiFrameworkException("Provider request failed: status=" + status + ", body=" + response.body());
                }
                last = new AiFrameworkException("Retryable HTTP status: " + status);
            } catch (IOException e) {
                last = new AiFrameworkException("I/O error while calling provider", e);
                if (i == attempts - 1) {
                    throw last;
                }
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
}
