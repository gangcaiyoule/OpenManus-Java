package com.openmanus.smoke.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.aiframework.runtime.AiSessionSandboxGateway;
import com.openmanus.aiframework.runtime.AiSessionSandboxInfo;
import com.openmanus.agent.tool.BrowserTool;
import com.openmanus.domain.service.ExecutionEventPort;
import com.openmanus.smoke.SmokeTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@Tag("smoke")
@DisplayName("BrowserTool Smoke Tests")
class BrowserToolSmokeTest implements SmokeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TEST_SESSION_ID = "test-session";
    private static final String TEST_USER_ID = "001";

    private AiSessionSandboxGateway mockGateway;
    private ExecutionEventPort mockExecutionEventPort;
    private BrowserTool browserTool;

    @BeforeEach
    void setUp() {
        MDC.put("sessionId", TEST_SESSION_ID);
        MDC.put("userId", TEST_USER_ID);
        mockGateway = mock(AiSessionSandboxGateway.class);
        mockExecutionEventPort = mock(ExecutionEventPort.class);
        browserTool = new BrowserTool(mockGateway, mockExecutionEventPort);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("openUrl should normalize url and return json")
    void openUrl_normalizesAndReturnsJson() throws Exception {
        when(mockGateway.getOrCreateSandbox(TEST_USER_ID)).thenReturn(new AiSessionSandboxInfo(
                TEST_USER_ID, null, "/workspace", "https://vnc.local", null, "RUNNING"
        ));
        String result = browserTool.openUrl("example.com/path");
        JsonNode node = MAPPER.readTree(result);
        assertThat(node.get("url").asText()).isEqualTo("https://example.com/path");
        assertThat(node.get("previewMode").asText()).isEqualTo("vnc");
        assertThat(node.get("sandboxVncUrl").asText()).contains("vnc.local");
        verify(mockGateway).openBrowserUrl(TEST_USER_ID, "https://example.com/path");
        verify(mockExecutionEventPort).recordCustomEvent(argThat(event -> event.getEventType().name().equals("BROWSER_URL_OPENED")));
    }

    @Test
    @DisplayName("ensureSandbox should create sandbox when not available")
    void ensureSandbox_createsSandboxWhenMissing() throws Exception {
        when(mockGateway.getSandboxInfo(anyString())).thenReturn(Optional.empty());
        when(mockGateway.getOrCreateSandbox(anyString())).thenReturn(new AiSessionSandboxInfo(
                TEST_SESSION_ID, null, "/workspace", "https://vnc.local", null, "RUNNING"
        ));

        String result = browserTool.ensureSandbox();
        JsonNode node = MAPPER.readTree(result);
        assertThat(node.get("sandboxAvailable").asBoolean()).isTrue();
        assertThat(node.get("sandboxVncUrl").asText()).contains("vnc.local");
        verify(mockGateway, atLeastOnce()).getOrCreateSandbox(anyString());
        verify(mockExecutionEventPort).recordCustomEvent(argThat(event -> event.getEventType().name().equals("VNC_READY")));
    }
}
