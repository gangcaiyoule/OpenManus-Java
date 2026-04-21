package com.openmanus.infra.web;

import com.openmanus.domain.service.WebProxyResult;
import com.openmanus.domain.service.WebProxyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(prefix = "openmanus.web-proxy", name = "enabled", havingValue = "true")
@Slf4j
public class WebProxyController {
    private final WebProxyService webProxyService;

    public WebProxyController(WebProxyService webProxyService) {
        this.webProxyService = webProxyService;
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
            try {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            } catch (IOException ignored) {}
        } catch (Exception e) {
            log.error("Proxy error", e);
            try {
                response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Proxy upstream request failed");
            } catch (IOException ignored) {}
        }
    }
    
    /**
     * 获取代理 URL
     * 前端可以调用此接口获取某个 URL 的代理地址
     */
    @GetMapping("/url")
    @Operation(summary = "Get Proxy URL", description = "Returns the proxy URL for a given target URL")
    public String getProxyUrl(@RequestParam("target") String targetUrl) {
        try {
            String encoded = Base64.getUrlEncoder()
                    .encodeToString(webProxyService.validateTargetUrl(targetUrl).getBytes(StandardCharsets.UTF_8));
            return "/api/proxy/web?url=" + encoded;
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
}
