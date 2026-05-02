package com.openmanus.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openmanus.aiframework.runtime.AiProxyConfig;
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
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static com.openmanus.aiframework.runtime.AiLogMarkers.TO_FRONTEND;

/**
 * Web content fetch tool - focuses on抓取网页内容并落快照（url -> path + preview）。
 */
@Slf4j
public class WebFetchTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_TIMEOUT_MS = DEFAULT_TIMEOUT_SECONDS * 1000;
    private static final int MAX_CONTENT_LENGTH = 10000;
    private static final int MAX_RESULT_LENGTH = 8000;

    private static final String USER_AGENT_BROWSER = "Mozilla/5.0 (compatible; OpenManus/1.0)";
    private static final int HTTP_OK = 200;
    private static final String MSG_ACCESS_FAILED = "访问失败，HTTP状态码: ";
    private static final String MSG_CONTENT_TRUNCATED = "\n... (内容已截断)";
    private static final String MSG_RESULT_TRUNCATED = "\n... (结果已截断)";

    private final AiSessionSandboxGateway sessionSandboxGateway;
    private final AiProxyConfig proxyConfig;
    private final ExecutionEventPort executionEventPort;

    public WebFetchTool(AiSessionSandboxGateway sessionSandboxGateway,
                        AiProxyConfig proxyConfig,
                        ExecutionEventPort executionEventPort) {
        this.sessionSandboxGateway = sessionSandboxGateway;
        this.proxyConfig = proxyConfig;
        this.executionEventPort = executionEventPort;
    }

    @AiTool(value = "在真实浏览器中展示网页，同时抓取网页内容并落本地快照", name = "browser_fetch_web")
    public String browseWeb(@AiParam("网页 URL") String url) {
        try {
            url = normalizeUrl(url);
            log.info("访问网页: {}", url);
            log.info(TO_FRONTEND, "│  🌐 WEB · 网页抓取模块");
            log.info(TO_FRONTEND, "│  📄 正在访问: {}", url);

            AiSessionSandboxInfo sandboxInfo = openInRealBrowser(url);
            if (sandboxInfo != null) {
                WebSearchEventSupport.emit(
                        executionEventPort,
                        "WebFetchTool",
                        AgentExecutionEvent.EventType.BROWSER_URL_OPENED,
                        metadata(url, null, "vnc", null, null, null, null, safeText(sandboxInfo.vncUrl()))
                );
            }

            WebSearchEventSupport.emit(
                    executionEventPort,
                    "WebFetchTool",
                    AgentExecutionEvent.EventType.WEB_FETCH_STARTED,
                    metadata(url, null, sandboxInfo == null ? "proxy" : "vnc", null, null, null, null,
                            sandboxInfo == null ? null : safeText(sandboxInfo.vncUrl()))
            );

            HttpURLConnection connection = createConnection(url, USER_AGENT_BROWSER);
            int responseCode = connection.getResponseCode();
            if (responseCode != HTTP_OK) {
                WebSearchEventSupport.emit(
                        executionEventPort,
                        "WebFetchTool",
                        AgentExecutionEvent.EventType.WEB_PREVIEW_BLOCKED,
                        metadata(url, null, "external", "HTTP_" + responseCode, "目标网页访问失败", null, null)
                );
                return MSG_ACCESS_FAILED + responseCode;
            }

            String content = readContent(connection);
            String contentType = connection.getContentType();
            return buildSnapshotResult(url, contentType, content);
        } catch (IOException e) {
            log.error("访问网页失败: {}", url, e);
            WebSearchEventSupport.emit(
                    executionEventPort,
                    "WebFetchTool",
                    AgentExecutionEvent.EventType.WEB_PREVIEW_BLOCKED,
                    metadata(url, null, "external", "FETCH_FAILED", e.getMessage(), null, null)
            );
            return "访问网页失败: " + e.getMessage();
        }
    }

    private String buildSnapshotResult(String url, String contentType, String content) {
        if (content == null) {
            return "访问网页失败: empty response";
        }

        if (contentType != null && !contentType.toLowerCase(Locale.ROOT).contains("text/html")) {
            WebSearchEventSupport.emit(
                    executionEventPort,
                    "WebFetchTool",
                    AgentExecutionEvent.EventType.WEB_PREVIEW_BLOCKED,
                    metadata(url, null, "external", "NON_HTML_CONTENT", "当前响应不是 HTML 页面", null, null)
            );
        }

        String sessionId = MDC.get("sessionId");
        if (sessionId == null || sessionId.isBlank() || sessionSandboxGateway == null) {
            String preview = content.length() > MAX_CONTENT_LENGTH
                    ? content.substring(0, MAX_CONTENT_LENGTH) + MSG_CONTENT_TRUNCATED
                    : content;
            return "网页内容:\n" + preview;
        }

        Path sandboxRoot = Path.of(sessionSandboxGateway.getWorkspaceRoot(sessionId)).normalize();
        Path snapshotDir = sandboxRoot.resolve(".openmanus").resolve("web");
        String fileName = "web-snapshot-" + Instant.now().toEpochMilli()
                + "-" + Integer.toHexString(url.toLowerCase(Locale.ROOT).hashCode()) + ".txt";
        Path snapshotPath = snapshotDir.resolve(fileName).normalize();
        if (!snapshotPath.startsWith(sandboxRoot)) {
            return "访问网页失败: 禁止写入沙盒外路径";
        }

        try {
            sessionSandboxGateway.writeTextFile(sessionId, snapshotPath.toString(), content);
        } catch (RuntimeException e) {
            log.warn("网页快照落盘失败: {}", e.getMessage());
            String preview = content.length() > MAX_CONTENT_LENGTH
                    ? content.substring(0, MAX_CONTENT_LENGTH) + MSG_CONTENT_TRUNCATED
                    : content;
            return "网页内容:\n" + preview;
        }

        boolean truncated = content.length() > MAX_RESULT_LENGTH;
        String preview = truncated ? content.substring(0, MAX_RESULT_LENGTH) + MSG_RESULT_TRUNCATED : content;

        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("url", url);
        root.put("path", snapshotPath.toString());
        root.put("preview", preview);
        root.put("truncated", truncated);
        root.put("originalChars", content.length());

        WebSearchEventSupport.emit(
                executionEventPort,
                "WebFetchTool",
                AgentExecutionEvent.EventType.WEB_FETCH_SNAPSHOT_READY,
                metadata(url, snapshotPath.toString(), "proxy", null, null, truncated, preview)
        );
        return root.toString();
    }

    private AiSessionSandboxInfo openInRealBrowser(String url) {
        String sessionId = MDC.get("sessionId");
        if (sessionId == null || sessionId.isBlank() || sessionSandboxGateway == null) {
            return null;
        }
        AiSessionSandboxInfo sandboxInfo = sessionSandboxGateway.getOrCreateSandbox(sessionId);
        sessionSandboxGateway.openBrowserUrl(sessionId, url);
        return sandboxInfo;
    }

    private static String normalizeUrl(String url) {
        if (url == null) {
            return "";
        }
        url = url.trim();
        if (url.isEmpty()) {
            return "";
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return "https://" + url;
        }
        return url;
    }

    private HttpURLConnection createConnection(String urlString, String userAgent) throws IOException {
        java.net.Proxy proxy = getProxy();
        HttpURLConnection connection;

        if (proxy != null) {
            connection = (HttpURLConnection) URI.create(urlString).toURL().openConnection(proxy);
        } else {
            connection = (HttpURLConnection) URI.create(urlString).toURL().openConnection();
        }

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(DEFAULT_TIMEOUT_MS);
        connection.setReadTimeout(DEFAULT_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", userAgent);
        return connection;
    }

    private java.net.Proxy getProxy() {
        if (proxyConfig != null && proxyConfig.enabled()) {
            String proxyUrl = proxyConfig.httpsProxy();
            if (proxyUrl == null || proxyUrl.isEmpty()) {
                proxyUrl = proxyConfig.httpProxy();
            }
            if (proxyUrl != null && !proxyUrl.isEmpty()) {
                try {
                    if (proxyUrl.contains("://")) {
                        proxyUrl = proxyUrl.substring(proxyUrl.indexOf("://") + 3);
                    }
                    String[] parts = proxyUrl.split(":");
                    if (parts.length == 2) {
                        String host = parts[0];
                        int port = Integer.parseInt(parts[1]);
                        return new java.net.Proxy(java.net.Proxy.Type.HTTP, new java.net.InetSocketAddress(host, port));
                    }
                } catch (Exception e) {
                    log.warn("代理配置解析失败: {}", proxyUrl, e);
                }
            }
        }
        return null;
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

    private static Map<String, Object> metadata(String activeUrl,
                                                String snapshotPath,
                                                String previewMode,
                                                String blockReason,
                                                String detail,
                                                Boolean truncated,
                                                String snapshotPreview) {
        return metadata(activeUrl, snapshotPath, previewMode, blockReason, detail, truncated, snapshotPreview, null);
    }

    private static Map<String, Object> metadata(String activeUrl,
                                                String snapshotPath,
                                                String previewMode,
                                                String blockReason,
                                                String detail,
                                                Boolean truncated,
                                                String snapshotPreview,
                                                String sandboxVncUrl) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("activeUrl", activeUrl);
        metadata.put("previewMode", previewMode);
        if (sandboxVncUrl != null && !sandboxVncUrl.isBlank()) {
            metadata.put("sandboxVncUrl", sandboxVncUrl);
        }
        if (snapshotPath != null && !snapshotPath.isBlank()) {
            metadata.put("snapshotPath", snapshotPath);
        }
        if (blockReason != null && !blockReason.isBlank()) {
            metadata.put("blockReason", blockReason);
        }
        if (detail != null && !detail.isBlank()) {
            metadata.put("detail", detail);
        }
        if (truncated != null) {
            metadata.put("truncated", truncated);
        }
        if (snapshotPreview != null && !snapshotPreview.isBlank()) {
            metadata.put("snapshotPreview", snapshotPreview);
        }
        return metadata;
    }

    private static String safeText(String text) {
        return text == null ? "" : text;
    }
}
