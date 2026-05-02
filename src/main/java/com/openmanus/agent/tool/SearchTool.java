package com.openmanus.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.aiframework.runtime.AiSearchConfig;
import com.openmanus.aiframework.runtime.AiSessionSandboxGateway;
import com.openmanus.aiframework.runtime.AiSessionSandboxInfo;
import com.openmanus.aiframework.tool.AiParam;
import com.openmanus.aiframework.tool.AiTool;
import com.openmanus.domain.model.AgentExecutionEvent;
import com.openmanus.domain.service.ExecutionEventPort;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.openmanus.aiframework.runtime.AiLogMarkers.TO_FRONTEND;

/**
 * Search tool - 专注搜索（BrowserTool 仅做浏览器操纵）。
 */
@Slf4j
public class SearchTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_TIMEOUT_MS = DEFAULT_TIMEOUT_SECONDS * 1000;
    private static final int MAX_SEARCH_RESULTS = 5;
    private static final int MAX_RESULT_LENGTH = 8000;
    private static final int MAX_RETRIES = 3;
    private static final int HTTP_OK = 200;

    private final AiSearchConfig searchConfig;
    private final ExecutionEventPort executionEventPort;
    private final AiSessionSandboxGateway sessionSandboxGateway;

    public SearchTool(AiSearchConfig searchConfig, ExecutionEventPort executionEventPort) {
        this(searchConfig, executionEventPort, null);
    }

    public SearchTool(AiSearchConfig searchConfig,
                      ExecutionEventPort executionEventPort,
                      AiSessionSandboxGateway sessionSandboxGateway) {
        this.searchConfig = searchConfig;
        this.executionEventPort = executionEventPort;
        this.sessionSandboxGateway = sessionSandboxGateway;
    }

    @AiTool(value = "搜索网络内容；只返回结构化搜索结果，不打开或展示搜索结果页", name = "search_web")
    public String searchWeb(@AiParam("搜索关键词") String query) {
        int retryCount = 0;
        Exception lastException = null;
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String displayUrl = "https://www.google.com/search?q=" + encodedQuery;

        // 纯搜索模式：只使用 Serper API，不打开 VNC 浏览器，也不驱动前端 Browser 面板。
        // 如果需要浏览器操作，请使用 browser_* 工具。
        WebSearchEventSupport.emit(
                executionEventPort,
                "SearchTool",
                AgentExecutionEvent.EventType.SEARCH_STARTED,
                metadata(query, displayUrl, null, "web", null, null, null, null)
        );

        while (retryCount < MAX_RETRIES) {
            try {
                if (retryCount > 0) {
                    log.info("搜索重试第 {} 次: {}", retryCount, query);
                    Thread.sleep(1000L * retryCount);
                }

                log.info(TO_FRONTEND, "│  🔍 SEARCH · 搜索模块");
                log.info(TO_FRONTEND, "│  📝 关键词: {}", query);

                String apiKey = searchConfig == null ? null : searchConfig.apiKey();
                if (apiKey == null || apiKey.isEmpty() || apiKey.startsWith("your-")) {
                    return fallbackSearch(query, encodedQuery, displayUrl);
                }

                return searchWithSerperApi(query, displayUrl);
            } catch (Exception e) {
                lastException = e;
                log.warn("搜索尝试 {} 失败: {}", retryCount + 1, e.getMessage());
                retryCount++;
            }
        }

        log.error("搜索最终失败: {}", query, lastException);
        return "搜索失败 (已重试 " + MAX_RETRIES + " 次): " + (lastException != null ? lastException.getMessage() : "Unknown error");
    }

    private String searchWithSerperApi(String query, String displayUrl) throws IOException {
        String endpoint = searchConfig.serperEndpoint();

        HttpURLConnection connection = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(DEFAULT_TIMEOUT_MS);
        connection.setReadTimeout(DEFAULT_TIMEOUT_MS);
        connection.setRequestProperty("X-API-KEY", searchConfig.apiKey());
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        String requestBody = String.format("{\"q\":\"%s\",\"num\":%d}",
                query.replace("\"", "\\\""),
                Math.min(searchConfig.maxResults(), MAX_SEARCH_RESULTS));

        try (OutputStream os = connection.getOutputStream()) {
            os.write(requestBody.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != HTTP_OK) {
            throw new IOException("Serper API 错误: HTTP " + responseCode);
        }

        String jsonResponse = readContent(connection);
        return parseSerperResults(jsonResponse, query, displayUrl);
    }

    private String parseSerperResults(String jsonResponse, String query, String displayUrl) {
        StringBuilder results = new StringBuilder();
        results.append("🔍 搜索结果: ").append(query).append("\n\n");
        List<Map<String, Object>> resultItems = new ArrayList<>();

        try {
            JsonNode root = OBJECT_MAPPER.readTree(jsonResponse);
            int count = 0;

            JsonNode organic = root.get("organic");
            if (organic != null && organic.isArray()) {
                for (JsonNode item : organic) {
                    if (count >= MAX_SEARCH_RESULTS) {
                        break;
                    }
                    String title = item.has("title") ? item.get("title").asText() : "";
                    String link = item.has("link") ? item.get("link").asText() : "";
                    String snippet = item.has("snippet") ? item.get("snippet").asText() : "";

                    if (!title.isEmpty() && !link.isEmpty()) {
                        count++;
                        resultItems.add(resultItem(count, title, link, snippet));
                        results.append(count).append(". **").append(title).append("**\n");
                        results.append("   🔗 ").append(link).append("\n");
                        if (!snippet.isEmpty()) {
                            results.append("   📝 ").append(trimTo(snippet, MAX_RESULT_LENGTH)).append("\n");
                        }
                        results.append("\n");
                    }
                }
            }

            if (count == 0) {
                results.append("未找到相关搜索结果，请尝试其他关键词。\n");
            }

            WebSearchEventSupport.emit(
                    executionEventPort,
                    "SearchTool",
                    AgentExecutionEvent.EventType.SEARCH_RESULTS_READY,
                    metadata(query, displayUrl, null, "web", resultItems, null, null)
            );
        } catch (Exception e) {
            results.append("搜索结果解析失败: ").append(e.getMessage()).append("\n");
            results.append("原始响应: ").append(trimTo(jsonResponse, MAX_RESULT_LENGTH)).append("\n");
        }

        return results.toString();
    }

    private String fallbackSearch(String query, String encodedQuery, String displayUrl) {
        WebSearchEventSupport.emit(
                executionEventPort,
                "SearchTool",
                AgentExecutionEvent.EventType.SEARCH_RESULTS_READY,
                metadata(query, displayUrl, null, "web", List.of(), "SERPER_API_NOT_CONFIGURED", "搜索 API 未配置")
        );
        return """
                🔍 搜索结果: %s

                搜索 API 未配置（Serper API Key 为空或为占位符）。
                可在配置中设置：
                - openmanus.search.api-key
                - openmanus.search.serper-endpoint（默认 https://google.serper.dev/search）

                你仍可在前端打开搜索页面：
                https://www.google.com/search?q=%s
                """.formatted(query, encodedQuery);
    }

    private AiSessionSandboxInfo openSearchPageInRealBrowser(String displayUrl) {
        String sessionId = MDC.get("sessionId");
        if (sessionId == null || sessionId.isBlank() || sessionSandboxGateway == null) {
            return null;
        }
        AiSessionSandboxInfo sandboxInfo = sessionSandboxGateway.getOrCreateSandbox(sessionId);
        sessionSandboxGateway.openBrowserUrl(sessionId, displayUrl);
        return sandboxInfo;
    }

    private static Map<String, Object> metadata(String query,
                                                String searchPageUrl,
                                                String activeUrl,
                                                String previewMode,
                                                List<Map<String, Object>> resultItems,
                                                String blockReason,
                                                String detail) {
        return metadata(query, searchPageUrl, activeUrl, previewMode, resultItems, blockReason, detail, null);
    }

    private static Map<String, Object> metadata(String query,
                                                String searchPageUrl,
                                                String activeUrl,
                                                String previewMode,
                                                List<Map<String, Object>> resultItems,
                                                String blockReason,
                                                String detail,
                                                String sandboxVncUrl) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("query", query);
        metadata.put("searchPageUrl", searchPageUrl);
        metadata.put("activeUrl", activeUrl == null ? searchPageUrl : activeUrl);
        metadata.put("previewMode", previewMode);
        if (sandboxVncUrl != null && !sandboxVncUrl.isBlank()) {
            metadata.put("sandboxVncUrl", sandboxVncUrl);
        }
        if (resultItems != null) {
            metadata.put("resultItems", resultItems);
        }
        if (blockReason != null && !blockReason.isBlank()) {
            metadata.put("blockReason", blockReason);
        }
        if (detail != null && !detail.isBlank()) {
            metadata.put("detail", detail);
        }
        return metadata;
    }

    private static Map<String, Object> resultItem(int rank, String title, String url, String snippet) {
        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        item.put("rank", rank);
        item.put("title", title);
        item.put("url", url);
        item.put("snippet", snippet == null ? "" : trimTo(snippet, MAX_RESULT_LENGTH));
        return item;
    }

    private static String trimTo(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "\n... (结果已截断)";
    }

    private static String readContent(HttpURLConnection connection) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }
}
