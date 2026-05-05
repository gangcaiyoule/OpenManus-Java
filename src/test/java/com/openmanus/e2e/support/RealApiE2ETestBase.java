package com.openmanus.e2e.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.WebApplication;
import com.openmanus.domain.model.AgentExecutionEvent;
import com.openmanus.infra.config.DotenvLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = WebApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "openmanus.chat-memory.store-type=in-memory",
                "openmanus.mcp.enabled=false",
                "openmanus.proxy.enabled=false",
                "openmanus.llm.default-llm.temperature=0.0",
                "openmanus.llm.default-llm.max-tokens=256"
        }
)
@ActiveProfiles("test")
public abstract class RealApiE2ETestBase {
    private static final String SPRING_LLM_MODEL_KEY = "openmanus.llm.default-llm.model";
    private static final String SPRING_LLM_BASE_URL_KEY = "openmanus.llm.default-llm.base-url";
    private static final String SPRING_LLM_API_KEY = "openmanus.llm.default-llm.api-key";

    static {
        DotenvLoader.loadFromWorkingDirectory();
        publishLiveLlmConfigToSpringProperties();
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String newConversationId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    protected JsonNode postJson(String path, String body) throws IOException {
        ResponseEntity<String> response = exchange(path, body);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotBlank();
        return objectMapper.readTree(response.getBody());
    }

    protected ResponseEntity<String> exchange(String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(url(path), new HttpEntity<>(body, headers), String.class);
    }

    protected JsonNode getJson(String path) throws IOException {
        ResponseEntity<String> response = restTemplate.getForEntity(url(path), String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotBlank();
        return objectMapper.readTree(response.getBody());
    }

    protected String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    protected List<AgentExecutionEvent> waitForSessionEvents(String sessionId,
                                                             Predicate<List<AgentExecutionEvent>> completed)
            throws IOException {
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        while (System.nanoTime() < deadline) {
            List<AgentExecutionEvent> events = fetchSessionEvents(sessionId);
            if (completed.test(events)) {
                return events;
            }
            sleep(250L);
        }
        List<AgentExecutionEvent> events = fetchSessionEvents(sessionId);
        throw new AssertionError("timed out waiting for session events for " + sessionId + ", observed=" + events.size());
    }

    protected static Predicate<List<AgentExecutionEvent>> hasTerminalExecutionEvent() {
        return events -> events.stream().anyMatch(event ->
                event.getEventType() == AgentExecutionEvent.EventType.AGENT_END
                        || event.getEventType() == AgentExecutionEvent.EventType.ERROR);
    }

    protected List<AgentExecutionEvent> fetchSessionEvents(String sessionId) throws IOException {
        ResponseEntity<String> response = restTemplate.getForEntity(
                url("/api/agent-monitoring/sessions/" + sessionId + "/events"),
                String.class
        );
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotBlank();
        return objectMapper.readerForListOf(AgentExecutionEvent.class).readValue(response.getBody());
    }

    private static void publishLiveLlmConfigToSpringProperties() {
        String model = firstNonBlank(
                System.getenv("OPENMANUS_LIVE_MODEL"),
                System.getenv("OPENMANUS_LIVE_MODEL_CANDIDATES"),
                System.getProperty("OPENMANUS_LIVE_MODEL"),
                System.getProperty("OPENMANUS_LIVE_MODEL_CANDIDATES"),
                System.getenv("OPENMANUS_LLM_DEFAULT_LLM_MODEL"),
                System.getProperty("OPENMANUS_LLM_DEFAULT_LLM_MODEL")
        );
        String baseUrl = firstNonBlank(
                System.getenv("OPENMANUS_LIVE_BASE_URL"),
                System.getProperty("OPENMANUS_LIVE_BASE_URL"),
                System.getenv("OPENMANUS_LLM_DEFAULT_LLM_BASE_URL"),
                System.getProperty("OPENMANUS_LLM_DEFAULT_LLM_BASE_URL")
        );
        String apiKey = firstNonBlank(
                System.getenv("OPENMANUS_LIVE_API_KEY"),
                System.getProperty("OPENMANUS_LIVE_API_KEY"),
                System.getenv("OPENMANUS_LLM_DEFAULT_LLM_API_KEY"),
                System.getProperty("OPENMANUS_LLM_DEFAULT_LLM_API_KEY")
        );

        assertThat(model)
                .withFailMessage("real e2e requires OPENMANUS_LIVE_* or OPENMANUS_LLM_DEFAULT_LLM_* model config")
                .isNotBlank();
        assertThat(baseUrl)
                .withFailMessage("real e2e requires OPENMANUS_LIVE_* or OPENMANUS_LLM_DEFAULT_LLM_* base-url config")
                .isNotBlank();
        assertThat(apiKey)
                .withFailMessage("real e2e requires OPENMANUS_LIVE_* or OPENMANUS_LLM_DEFAULT_LLM_* api-key config")
                .isNotBlank()
                .isNotEqualTo("test-api-key");

        System.setProperty(SPRING_LLM_MODEL_KEY, firstCsvValue(model));
        System.setProperty(SPRING_LLM_BASE_URL_KEY, baseUrl);
        System.setProperty(SPRING_LLM_API_KEY, apiKey);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String firstCsvValue(String value) {
        if (value == null) {
            return null;
        }
        int commaIndex = value.indexOf(',');
        if (commaIndex < 0) {
            return value.trim();
        }
        return value.substring(0, commaIndex).trim();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for live e2e result", e);
        }
    }
}
