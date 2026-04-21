package com.openmanus.infra.web;

import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSocketHeartbeatControllerTest {

    private final WebSocketHeartbeatController controller = new WebSocketHeartbeatController();

    @Test
    void shouldEchoClientTimestampWhenPayloadIsValid() {
        Map<String, Object> response = controller.handleHeartbeat(Map.of("timestamp", "12345"), namedPrincipal("tester"));

        assertEquals("ok", response.get("status"));
        assertEquals(12345L, response.get("clientTime"));
        assertTrue((Long) response.get("serverTime") > 0L);
    }

    @Test
    void shouldFallbackToZeroWhenPayloadIsNull() {
        Map<String, Object> response = controller.handleHeartbeat(null, namedPrincipal("tester"));

        assertEquals("ok", response.get("status"));
        assertEquals(0L, response.get("clientTime"));
        assertTrue((Long) response.get("serverTime") > 0L);
    }

    @Test
    void shouldFallbackToZeroWhenTimestampIsMissingOrInvalid() {
        Map<String, Object> missingTimestamp = controller.handleHeartbeat(Map.of("ping", true), null);
        Map<String, Object> invalidTimestamp = controller.handleHeartbeat(Map.of("timestamp", "not-a-number"), null);
        Map<String, Object> nullPayload = new HashMap<>();
        nullPayload.put("timestamp", null);
        Map<String, Object> nullTimestamp = controller.handleHeartbeat(nullPayload, null);

        assertEquals(0L, missingTimestamp.get("clientTime"));
        assertEquals(0L, invalidTimestamp.get("clientTime"));
        assertEquals(0L, nullTimestamp.get("clientTime"));
    }

    private static Principal namedPrincipal(String name) {
        return () -> name;
    }
}
