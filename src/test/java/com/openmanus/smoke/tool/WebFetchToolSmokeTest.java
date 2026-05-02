package com.openmanus.smoke.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.aiframework.runtime.AiProxyConfig;
import com.openmanus.aiframework.runtime.AiSessionSandboxGateway;
import com.openmanus.aiframework.runtime.AiSessionSandboxInfo;
import com.openmanus.agent.tool.WebFetchTool;
import com.openmanus.domain.service.ExecutionEventPort;
import com.openmanus.smoke.SmokeTest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.MDC;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("smoke")
@DisplayName("WebFetchTool Smoke Tests")
class WebFetchToolSmokeTest implements SmokeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TEST_SESSION_ID = "test-session";

    @TempDir
    Path tempDir;

    private AiSessionSandboxGateway mockGateway;
    private AiProxyConfig mockProxyConfig;
    private ExecutionEventPort mockExecutionEventPort;
    private WebFetchTool webFetchTool;
    private HttpServer testServer;
    private String testServerBaseUrl;

    @BeforeEach
    void setUp() throws Exception {
        MDC.put("sessionId", TEST_SESSION_ID);
        try {
            startTestServer();
        } catch (SocketException exception) {
            Assumptions.abort("Current sandbox does not allow loopback port binding");
        }
        mockGateway = mock(AiSessionSandboxGateway.class);
        mockProxyConfig = mock(AiProxyConfig.class);
        mockExecutionEventPort = mock(ExecutionEventPort.class);
        when(mockProxyConfig.enabled()).thenReturn(false);
        when(mockGateway.getOrCreateSandbox(TEST_SESSION_ID)).thenReturn(new AiSessionSandboxInfo(
                TEST_SESSION_ID, null, tempDir.toString(), "https://vnc.local", null, "RUNNING"
        ));
        when(mockGateway.getWorkspaceRoot(anyString())).thenReturn(tempDir.toString());
        doAnswer(invocation -> {
            String path = invocation.getArgument(1, String.class);
            String content = invocation.getArgument(2, String.class);
            Path target = Path.of(path);
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
            return null;
        }).when(mockGateway).writeTextFile(eq(TEST_SESSION_ID), anyString(), anyString());
        webFetchTool = new WebFetchTool(mockGateway, mockProxyConfig, mockExecutionEventPort);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        if (testServer != null) {
            testServer.stop(0);
        }
    }

    @Test
    @DisplayName("browseWeb should snapshot to sandbox path and return url/path/preview")
    void browseWeb_snapshots() throws Exception {
        String result = webFetchTool.browseWeb(testServerBaseUrl + "/page");
        JsonNode node = MAPPER.readTree(result);
        assertThat(node.get("url").asText()).contains("/page");
        Path snapshotPath = Path.of(node.get("path").asText());
        assertThat(Files.exists(snapshotPath)).isTrue();
        assertThat(node.get("preview").asText()).contains("Local Web Smoke Page");
        verify(mockGateway).openBrowserUrl(eq(TEST_SESSION_ID), eq(testServerBaseUrl + "/page"));
        verify(mockExecutionEventPort).recordCustomEvent(argThat(event -> event.getEventType().name().equals("BROWSER_URL_OPENED")));
        verify(mockExecutionEventPort).recordCustomEvent(argThat(event -> event.getEventType().name().equals("WEB_FETCH_STARTED")));
        verify(mockExecutionEventPort).recordCustomEvent(argThat(event -> event.getEventType().name().equals("WEB_FETCH_SNAPSHOT_READY")));
    }

    private void startTestServer() throws Exception {
        testServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        testServer.createContext("/page", exchange -> {
            String response = "<html><body><h1>Local Web Smoke Page</h1></body></html>";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        testServer.start();
        testServerBaseUrl = "http://127.0.0.1:" + testServer.getAddress().getPort();
    }
}
