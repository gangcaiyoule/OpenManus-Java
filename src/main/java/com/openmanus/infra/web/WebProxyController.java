package com.openmanus.infra.web;

import com.openmanus.domain.service.WebProxyResult;
import com.openmanus.domain.service.WebProxyService;
import com.openmanus.domain.service.WebPreviewDiagnostic;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Web 代理控制器 - 用于在 iframe 中显示被 X-Frame-Options 限制的网页
 * 
 * 原理：
 * 1. 后端代理请求目标网页
 * 2. 移除限制性的响应头（X-Frame-Options, Content-Security-Policy 的 frame-ancestors）
 * 3. 重写页面中的相对链接为代理链接
 * 4. 返回处理后的内容给前端
 * 
 * 注意：此功能仅用于开发和演示目的
 */
@RestController
@RequestMapping("/api/proxy")
@Tag(name = "Web Proxy", description = "Proxy for viewing websites in iframe that have frame restrictions")
@Slf4j
public class WebProxyController {
    private final WebProxyService webProxyService;
    private final com.openmanus.infra.config.OpenManusProperties properties;

    public WebProxyController(WebProxyService webProxyService,
                              com.openmanus.infra.config.OpenManusProperties properties) {
        this.webProxyService = webProxyService;
        this.properties = properties;
    }

    /**
     * 代理访问网页
     * 
     * @param url Base64 编码的目标 URL
     * @param response HTTP 响应
     */
    @GetMapping("/web")
    @Operation(
        summary = "Proxy Web Page",
        description = "Proxies a web page and removes frame restriction headers (X-Frame-Options, CSP frame-ancestors) to allow iframe embedding"
    )
    public void proxyWeb(
            @RequestParam("url") String url,
            HttpServletResponse response) {
        try {
            String targetUrl = decodeTargetUrl(url);
            if (!isProxyEnabled()) {
                writeDiagnosticHtml(
                        response,
                        HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "proxy-disabled",
                        "网页代理未启用",
                        "当前服务未开启网页代理，无法直接在嵌入视图中预览。前端应自动切换到 VNC 或外部打开。"
                );
                return;
            }
            log.debug("Proxying web page: {}", targetUrl);
            WebProxyResult proxyResult = webProxyService.fetch(targetUrl);
            if (proxyResult.isRedirect()) {
                response.sendRedirect(proxyResult.redirectLocation());
                return;
            }
            response.setStatus(proxyResult.statusCode());
            response.setContentType(proxyResult.contentType());
            response.setCharacterEncoding("UTF-8");
            proxyResult.headers().forEach((name, values) -> {
                if (name == null || values == null) {
                    return;
                }
                for (String value : values) {
                    if (value != null) {
                        response.addHeader(name, value);
                    }
                }
            });
            response.setContentLength(proxyResult.body().length);
            response.getOutputStream().write(proxyResult.body());
        } catch (IllegalArgumentException e) {
            log.warn("Reject proxy request: {}", e.getMessage());
            writeDiagnosticHtml(response, HttpServletResponse.SC_BAD_REQUEST, "invalid-url", "代理 URL 不合法", e.getMessage());
        } catch (Exception e) {
            log.error("Proxy error", e);
            writeDiagnosticHtml(
                    response,
                    HttpServletResponse.SC_BAD_GATEWAY,
                    "fetch-failed",
                    "代理抓取失败",
                    "上游网页抓取失败，建议切换到 VNC 查看真实页面。"
            );
        }
    }
    
    /**
     * 获取代理 URL
     * 前端可以调用此接口获取某个 URL 的代理地址
     */
    @GetMapping("/url")
    @Operation(summary = "Get Proxy URL", description = "Returns the proxy URL for a given target URL")
    public String getProxyUrl(@RequestParam("target") String targetUrl) {
        if (!isProxyEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "proxy-disabled");
        }
        try {
            String encoded = Base64.getUrlEncoder()
                    .encodeToString(webProxyService.validateTargetUrl(targetUrl).getBytes(StandardCharsets.UTF_8));
            return "/api/proxy/web?url=" + encoded;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @GetMapping("/inspect")
    @Operation(summary = "Inspect Proxy Preview", description = "Returns structured preview diagnostics for a target URL")
    public WebPreviewDiagnostic inspect(@RequestParam("target") String targetUrl) {
        try {
            return webProxyService.inspect(targetUrl, isProxyEnabled());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    private String decodeTargetUrl(String url) {
        try {
            return new String(Base64.getUrlDecoder().decode(url), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Proxy URL must be base64url encoded", e);
        }
    }

    private boolean isProxyEnabled() {
        return properties != null
                && properties.getWebProxy() != null
                && properties.getWebProxy().isEnabled();
    }

    private void writeDiagnosticHtml(HttpServletResponse response,
                                     int statusCode,
                                     String reasonCode,
                                     String title,
                                     String message) {
        response.setStatus(statusCode);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/html; charset=UTF-8");
        response.setHeader("X-OpenManus-Proxy-Status", reasonCode);
        String body = """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>%s</title>
                  <style>
                    body { margin:0; font-family: -apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif; background:#0f172a; color:#e2e8f0; display:flex; min-height:100vh; align-items:center; justify-content:center; }
                    .card { max-width:680px; padding:28px; border-radius:16px; background:#111827; box-shadow:0 20px 60px rgba(0,0,0,.35); }
                    .badge { display:inline-block; margin-bottom:12px; padding:4px 10px; border-radius:999px; background:#1e293b; color:#93c5fd; font-size:12px; }
                    h1 { margin:0 0 12px; font-size:22px; }
                    p { margin:0; line-height:1.7; color:#cbd5e1; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <div class="badge">%s</div>
                    <h1>%s</h1>
                    <p>%s</p>
                  </div>
                </body>
                </html>
                """.formatted(escapeHtml(title), escapeHtml(reasonCode), escapeHtml(title), escapeHtml(message));
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            response.setContentLength(bytes.length);
            response.getOutputStream().write(bytes);
        } catch (IOException e) {
            log.warn("Failed to write diagnostic HTML: {}", e.getMessage());
        }
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
