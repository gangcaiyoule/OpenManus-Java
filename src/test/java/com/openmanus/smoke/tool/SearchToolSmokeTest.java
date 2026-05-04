package com.openmanus.smoke.tool;

import com.openmanus.aiframework.runtime.AiSearchConfig;
import com.openmanus.aiframework.runtime.AiSessionSandboxGateway;
import com.openmanus.aiframework.runtime.AiSessionSandboxInfo;
import com.openmanus.agent.tool.SearchTool;
import com.openmanus.domain.service.ExecutionEventPort;
import com.openmanus.smoke.SmokeTest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@Tag("smoke")
@DisplayName("SearchTool Smoke Tests")
class SearchToolSmokeTest implements SmokeTest {

    private AiSearchConfig mockSearchConfig;
    private ExecutionEventPort mockExecutionEventPort;
    private AiSessionSandboxGateway mockGateway;
    private SearchTool searchTool;
    private HttpServer testServer;
    private String testServerBaseUrl;

    @BeforeEach
    void setUp() throws Exception {
        try {
            startTestServer();
        } catch (SocketException exception) {
            Assumptions.abort("Current sandbox does not allow loopback port binding");
        }
        mockSearchConfig = mock(AiSearchConfig.class);
        mockExecutionEventPort = mock(ExecutionEventPort.class);
        mockGateway = mock(AiSessionSandboxGateway.class);
        MDC.put("sessionId", "test-session");
        when(mockGateway.getOrCreateSandbox("test-session")).thenReturn(new AiSessionSandboxInfo(
                "test-session", null, "/workspace", "https://vnc.local", null, "RUNNING"
        ));
        when(mockSearchConfig.apiKey()).thenReturn("test-api-key");
        when(mockSearchConfig.serperEndpoint()).thenReturn(testServerBaseUrl + "/search");
        when(mockSearchConfig.maxResults()).thenReturn(5);
        searchTool = new SearchTool(mockSearchConfig, mockExecutionEventPort, mockGateway);
    }

    @AfterEach
    void tearDown() {
        if (testServer != null) {
            testServer.stop(0);
        }
        MDC.clear();
    }

    @Test
    @DisplayName("searchWeb should return serper results in parser-friendly format")
    void searchWeb_returnsResults() {
        String result = searchTool.searchWeb("test query");
        assertThat(result)
                .contains("搜索结果: test query")
                .contains("1. **Local Result**")
                .contains("Link: http://example.local")
                .contains("Snippet: AI agent local smoke result");
        verify(mockExecutionEventPort).recordCustomEvent(argThat(event -> event.getEventType().name().equals("SEARCH_STARTED")));
        verify(mockExecutionEventPort).recordCustomEvent(argThat(event -> event.getEventType().name().equals("SEARCH_RESULTS_READY")));
        verify(mockGateway, never()).openBrowserUrl(anyString(), anyString());
    }

    @Test
    @DisplayName("searchWeb should return fallback message when api key is placeholder")
    void searchWeb_placeholderKey_fallback() {
        when(mockSearchConfig.apiKey()).thenReturn("your-serper-api-key");
        String result = searchTool.searchWeb("test query");
        assertThat(result).contains("搜索 API 未配置");
        assertThat(result).contains("https://www.google.com/search?q=");
    }

    @Test
    @DisplayName("searchWeb should not trim long snippets before executor budget")
    void searchWeb_longSnippet_returnsRawResult() {
        String result = searchTool.searchWeb("long query");
        assertThat(result).contains("LONG_SNIPPET_START");
        assertThat(result).contains("s".repeat(8500));
        assertThat(result).doesNotContain("结果已截断");
    }

    private void startTestServer() throws Exception {
        testServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        testServer.createContext("/search", exchange -> {
            String longSnippet = "LONG_SNIPPET_START " + "s".repeat(9000) + " LONG_SNIPPET_END";
            String response = """
                    {
                      "organic": [
                        {
                          "title": "Local Result",
                          "link": "http://example.local",
                          "snippet": "AI agent local smoke result %s"
                        }
                      ]
                    }
                    """.formatted(longSnippet);
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        testServer.start();
        testServerBaseUrl = "http://127.0.0.1:" + testServer.getAddress().getPort();
    }
}
