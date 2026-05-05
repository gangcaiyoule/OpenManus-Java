package com.openmanus.domain.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class WebProxyService {

    private static final String PROXY_PATH_PREFIX = "/api/proxy/web?url=";

    private final WebProxyFetchPort webProxyFetchPort;

    public WebProxyService(WebProxyFetchPort webProxyFetchPort) {
        this.webProxyFetchPort = webProxyFetchPort;
    }

    public WebProxyResult fetch(String targetUrl) throws IOException {
        return webProxyFetchPort.fetch(targetUrl);
    }

    public String validateTargetUrl(String targetUrl) {
        return webProxyFetchPort.normalizeTargetUrl(targetUrl);
    }

    public WebPreviewDiagnostic inspect(String targetUrl, boolean enabled) {
        String normalizedTargetUrl = validateTargetUrl(targetUrl);
        String proxyUrl = PROXY_PATH_PREFIX + java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(normalizedTargetUrl.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        if (!enabled) {
            return new WebPreviewDiagnostic(
                    false,
                    normalizedTargetUrl,
                    proxyUrl,
                    "proxy-disabled",
                    "proxy-disabled",
                    "网页代理未启用，无法进行站内预览",
                    "external",
                    null,
                    null,
                    false,
                    true
            );
        }

        try {
            WebProxyResult result = fetch(normalizedTargetUrl);
            if (result.isRedirect()) {
                return new WebPreviewDiagnostic(
                        true,
                        normalizedTargetUrl,
                        proxyUrl,
                        "redirected",
                        "redirected",
                        "目标网页发生重定向，建议切换 VNC 查看真实跳转过程",
                        "vnc",
                        result.redirectLocation(),
                        result.contentType(),
                        false,
                        true
                );
            }
            if (!result.isHtml()) {
                return new WebPreviewDiagnostic(
                        true,
                        normalizedTargetUrl,
                        proxyUrl,
                        "non-html",
                        "non-html",
                        "当前响应不是 HTML 页面，无法在网页面板中稳定展示",
                        "external",
                        null,
                        result.contentType(),
                        false,
                        true
                );
            }
            if (isChallengeOrUnstableProxyPage(result)) {
                return new WebPreviewDiagnostic(
                        true,
                        normalizedTargetUrl,
                        proxyUrl,
                        "proxy-blocked",
                        "challenge-page",
                        "目标网页返回了挑战/跳转中间页，代理预览无法稳定展示，需切换真实浏览器",
                        "vnc",
                        null,
                        result.contentType(),
                        false,
                        true
                );
            }
            return new WebPreviewDiagnostic(
                    true,
                    normalizedTargetUrl,
                    proxyUrl,
                    "proxy-rendered",
                    "",
                    "",
                    "proxy",
                    null,
                    result.contentType(),
                    true,
                    false
            );
        } catch (IOException e) {
            return new WebPreviewDiagnostic(
                    true,
                    normalizedTargetUrl,
                    proxyUrl,
                    "fetch-failed",
                    "fetch-failed",
                    "代理抓取网页失败: " + e.getMessage(),
                    "vnc",
                    null,
                    null,
                    false,
                    true
            );
        }
    }

    private boolean isChallengeOrUnstableProxyPage(WebProxyResult result) {
        if (!result.isHtml() || result.body() == null || result.body().length == 0) {
            return false;
        }
        String body = new String(result.body(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        return body.contains("/httpservice/retry/enablejs")
                || body.contains("if you're having trouble accessing google search")
                || body.contains("如果您在几秒钟内没有被重定向")
                || body.contains("数秒たってもリダイレクトされない")
                || body.contains("automated queries")
                || body.contains("unusual traffic from your computer network")
                || body.contains("/sorry/index")
                || body.contains("cf-browser-verification")
                || body.contains("captcha");
    }
}
