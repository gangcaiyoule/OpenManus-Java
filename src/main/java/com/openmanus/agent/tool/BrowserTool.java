package com.openmanus.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openmanus.aiframework.runtime.AiSessionSandboxGateway;
import com.openmanus.aiframework.runtime.AiSessionSandboxInfo;
import com.openmanus.aiframework.tool.AiParam;
import com.openmanus.aiframework.tool.AiTool;
import com.openmanus.domain.model.AgentExecutionEvent;
import com.openmanus.domain.service.ExecutionEventPort;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static com.openmanus.aiframework.runtime.AiLogMarkers.TO_FRONTEND;

/**
 * Browser tool - focuses on操纵浏览器/沙箱（而不是搜索/抓取）。
 *
 * 当前能力：
 * 1) 让前端 Web iframe 打开 URL（通过 TO_FRONTEND 日志驱动 currentUrl）。
 * 2) 创建/确保会话 VNC 沙箱可用，并返回 vncUrl（前端轮询 session info 获取并展示）。
 */
@Slf4j
public class BrowserTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AiSessionSandboxGateway sessionSandboxGateway;
    private final ExecutionEventPort executionEventPort;

    public BrowserTool(AiSessionSandboxGateway sessionSandboxGateway, ExecutionEventPort executionEventPort) {
        this.sessionSandboxGateway = sessionSandboxGateway;
        this.executionEventPort = executionEventPort;
    }

    @AiTool(value = "在真实浏览器/VNC 中打开指定 URL；所有浏览器操作必须使用 browser_* 工具", name = "browser_open_url")
    public String openUrl(@AiParam("网页 URL") String url) {
        String normalized = normalizeUrl(url);
        if (normalized.isBlank()) {
            return jsonError("invalid url");
        }

        log.info(TO_FRONTEND, "│  🌐 BROWSER · 浏览器操纵模块");
        log.info(TO_FRONTEND, "│  📄 正在访问: {}", normalized);

        String sessionId = MDC.get("sessionId");
        AiSessionSandboxInfo sandboxInfo = null;
        if (sessionId != null && !sessionId.isBlank() && sessionSandboxGateway != null) {
            sandboxInfo = sessionSandboxGateway.getOrCreateSandbox(sessionId);
            sessionSandboxGateway.openBrowserUrl(sessionId, normalized);
        }

        WebSearchEventSupport.emit(
                executionEventPort,
                "BrowserTool",
                AgentExecutionEvent.EventType.BROWSER_URL_OPENED,
                metadata(normalized, "vnc", null, sandboxInfo == null ? null : safeText(sandboxInfo.vncUrl()))
        );

        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("url", normalized);
        root.put("previewMode", sandboxInfo == null || safeText(sandboxInfo.vncUrl()).isBlank() ? "web" : "vnc");
        root.put("sandboxVncUrl", sandboxInfo == null ? "" : safeText(sandboxInfo.vncUrl()));
        return root.toString();
    }

    @AiTool(value = "确保 VNC 沙箱就绪并返回 VNC URL（如可用）", name = "browser_ensure_sandbox")
    public String ensureSandbox() {
        String sessionId = MDC.get("sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            return jsonError("missing sessionId");
        }
        if (sessionSandboxGateway == null) {
            return jsonError("sandbox gateway not configured");
        }

        try {
            AiSessionSandboxInfo info = sessionSandboxGateway.getSandboxInfo(sessionId).orElse(null);
            if (info == null || !info.isAvailable()) {
                log.info(TO_FRONTEND, "┌──────────────────────────────────────────────────────────┐");
                log.info(TO_FRONTEND, "│  🖥️ SANDBOX · 可视化沙箱环境                            │");
                log.info(TO_FRONTEND, "├──────────────────────────────────────────────────────────┤");
                log.info(TO_FRONTEND, "│  ⚡ 正在初始化安全沙箱环境...                              │");
                log.info(TO_FRONTEND, "└──────────────────────────────────────────────────────────┘");
                info = sessionSandboxGateway.getOrCreateSandbox(sessionId);
                log.info(TO_FRONTEND, "│  ✅ 沙箱已就绪 · VNC 可视化界面已开放                        │");
            }

            WebSearchEventSupport.emit(
                    executionEventPort,
                    "BrowserTool",
                    AgentExecutionEvent.EventType.VNC_READY,
                    metadata("", "vnc", null, info == null ? "" : safeText(info.vncUrl()))
            );

            ObjectNode root = OBJECT_MAPPER.createObjectNode();
            root.put("sessionId", sessionId);
            root.put("sandboxAvailable", info != null && info.isAvailable());
            root.put("sandboxStatus", info == null ? "" : safeText(info.status()));
            root.put("sandboxVncUrl", info == null ? "" : safeText(info.vncUrl()));
            return root.toString();
        } catch (RuntimeException e) {
            return jsonError("ensure sandbox failed: " + e.getMessage());
        }
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

    private static String safeText(String text) {
        return text == null ? "" : text;
    }

    private static String jsonError(String message) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("error", message == null ? "" : message);
        return root.toString();
    }

    private static Map<String, Object> metadata(String activeUrl,
                                                String previewMode,
                                                String blockReason,
                                                String sandboxVncUrl) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("activeUrl", activeUrl);
        metadata.put("previewMode", previewMode);
        if (blockReason != null && !blockReason.isBlank()) {
            metadata.put("blockReason", blockReason);
        }
        if (sandboxVncUrl != null && !sandboxVncUrl.isBlank()) {
            metadata.put("sandboxVncUrl", sandboxVncUrl);
        }
        return metadata;
    }
}
