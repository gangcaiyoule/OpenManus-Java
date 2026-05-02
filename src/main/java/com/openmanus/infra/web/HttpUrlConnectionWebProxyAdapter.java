package com.openmanus.infra.web;

import com.openmanus.domain.service.WebProxyConfigProvider;
import com.openmanus.domain.service.WebProxyFetchPort;
import com.openmanus.domain.service.WebProxyResult;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Base64;

@Component
@Slf4j
public class HttpUrlConnectionWebProxyAdapter implements WebProxyFetchPort {

    static final int TIMEOUT_MS = 15000;
    static final int MAX_CONTENT_SIZE = 5 * 1024 * 1024;
    static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final List<String> HEADERS_TO_REMOVE = List.of(
            "x-frame-options",
            "content-security-policy",
            "content-security-policy-report-only",
            "transfer-encoding",
            "content-length",
            "content-encoding"
    );

    private final WebProxyConfigProvider proxyConfig;

    public HttpUrlConnectionWebProxyAdapter(WebProxyConfigProvider proxyConfig) {
        this.proxyConfig = proxyConfig;
    }

    @Override
    public WebProxyResult fetch(String targetUrl) throws IOException {
        String normalizedUrl = WebProxyTargetValidator.normalizeAndValidate(targetUrl);
        URI requestUri = URI.create(normalizedUrl);

        HttpURLConnection connection = openConnection(normalizedUrl);
        connection.setInstanceFollowRedirects(false);

        try {
            int responseCode = connection.getResponseCode();
            if (responseCode >= 300 && responseCode < 400) {
                String redirectUrl = resolveRedirectUrl(connection.getHeaderField("Location"), requestUri);
                if (redirectUrl != null) {
                    return new WebProxyResult(responseCode, null, Map.of(), new byte[0], buildProxyRedirectUrl(redirectUrl));
                }
            }

            String contentType = connection.getContentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = "text/html; charset=utf-8";
            }

            try (InputStream stream = openResponseStream(connection, responseCode)) {
                byte[] body = readBody(stream);
                if (isHtml(contentType)) {
                    String processed = processHtmlContent(new String(body, StandardCharsets.UTF_8), requestUri);
                    body = processed.getBytes(StandardCharsets.UTF_8);
                }
                return new WebProxyResult(
                        responseCode,
                        contentType,
                        filterHeaders(connection.getHeaderFields()),
                        body,
                        null
                );
            }
        } finally {
            connection.disconnect();
        }
    }

    @Override
    public String normalizeTargetUrl(String targetUrl) {
        return WebProxyTargetValidator.normalizeAndValidate(targetUrl);
    }

    HttpURLConnection openConnection(String urlString) throws IOException {
        Proxy proxy = resolveProxy();
        URL url = URI.create(urlString).toURL();
        HttpURLConnection connection = (HttpURLConnection) (proxy == null ? url.openConnection() : url.openConnection(proxy));
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        return connection;
    }

    Proxy resolveProxy() {
        if (proxyConfig == null || !proxyConfig.enabled()) {
            return null;
        }
        String proxyUrl = proxyConfig.httpsProxy();
        if (proxyUrl == null || proxyUrl.isBlank()) {
            proxyUrl = proxyConfig.httpProxy();
        }
        if (proxyUrl == null || proxyUrl.isBlank()) {
            return null;
        }
        try {
            String normalized = proxyUrl.contains("://")
                    ? proxyUrl.substring(proxyUrl.indexOf("://") + 3)
                    : proxyUrl;
            String[] parts = normalized.split(":");
            if (parts.length != 2) {
                return null;
            }
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(parts[0], Integer.parseInt(parts[1])));
        } catch (RuntimeException e) {
            log.warn("Proxy config parse error: {}", proxyUrl, e);
            return null;
        }
    }

    private static InputStream openResponseStream(HttpURLConnection connection, int responseCode) throws IOException {
        try {
            return connection.getInputStream();
        } catch (IOException e) {
            InputStream errorStream = connection.getErrorStream();
            if (errorStream == null) {
                throw new IOException("Failed to fetch: HTTP " + responseCode, e);
            }
            return errorStream;
        }
    }

    private static Map<String, List<String>> filterHeaders(Map<String, List<String>> headerFields) {
        Map<String, List<String>> filtered = new LinkedHashMap<>();
        headerFields.forEach((name, values) -> {
            if (name == null || HEADERS_TO_REMOVE.contains(name.toLowerCase()) || values == null) {
                return;
            }
            List<String> sanitizedValues = new ArrayList<>(values.size());
            for (String value : values) {
                if (value != null) {
                    sanitizedValues.add(value);
                }
            }
            if (!sanitizedValues.isEmpty()) {
                filtered.put(name, List.copyOf(sanitizedValues));
            }
        });
        return filtered;
    }

    private static byte[] readBody(InputStream inputStream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int length;
        int totalRead = 0;
        while ((length = inputStream.read(buffer)) != -1 && totalRead < MAX_CONTENT_SIZE) {
            int writable = Math.min(length, MAX_CONTENT_SIZE - totalRead);
            result.write(buffer, 0, writable);
            totalRead += writable;
        }
        return result.toByteArray();
    }

    private static boolean isHtml(String contentType) {
        return contentType != null && contentType.toLowerCase().contains("text/html");
    }

    private static String processHtmlContent(String content, URI requestUri) {
        Document document = Jsoup.parse(content, currentDocumentBase(requestUri));
        ensureHead(document);

        rewriteAttributeUrls(document, requestUri, "a[href]", "href");
        rewriteAttributeUrls(document, requestUri, "link[href]", "href");
        rewriteAttributeUrls(document, requestUri, "script[src]", "src");
        rewriteAttributeUrls(document, requestUri, "img[src]", "src");
        rewriteAttributeUrls(document, requestUri, "iframe[src]", "src");
        rewriteAttributeUrls(document, requestUri, "source[src]", "src");
        rewriteAttributeUrls(document, requestUri, "video[src]", "src");
        rewriteAttributeUrls(document, requestUri, "audio[src]", "src");
        rewriteAttributeUrls(document, requestUri, "embed[src]", "src");
        rewriteAttributeUrls(document, requestUri, "object[data]", "data");
        rewriteAttributeUrls(document, requestUri, "track[src]", "src");
        rewriteAttributeUrls(document, requestUri, "form[action]", "action");

        injectProxyRuntimePatch(document);
        return document.outerHtml();
    }

    private static void ensureHead(Document document) {
        if (document.head() != null) {
            return;
        }
        Element html = document.selectFirst("html");
        if (html == null) {
            document.appendElement("html");
            html = document.selectFirst("html");
        }
        if (html != null) {
            html.prependElement("head");
        }
    }

    private static void rewriteAttributeUrls(Document document, URI requestUri, String selector, String attribute) {
        for (Element element : document.select(selector)) {
            String rawValue = element.attr(attribute);
            if (rawValue == null || rawValue.isBlank()) {
                continue;
            }
            String normalized = rawValue.trim();
            if (isBypassScheme(normalized)) {
                continue;
            }
            try {
                URI resolved = requestUri.resolve(normalized);
                String scheme = resolved.getScheme();
                if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                    continue;
                }
                element.attr(attribute, buildProxyRedirectUrl(resolved.toString()));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private static boolean isBypassScheme(String value) {
        String lower = value.toLowerCase();
        return lower.startsWith("javascript:")
                || lower.startsWith("mailto:")
                || lower.startsWith("tel:")
                || lower.startsWith("data:")
                || lower.startsWith("blob:")
                || lower.startsWith("#");
    }

    private static void injectProxyRuntimePatch(Document document) {
        String patchScript = """
                (function () {
                  const PROXY_PREFIX = '/api/proxy/web?url=';
                  function toBase64Url(value) {
                    return btoa(value).replace(/\\+/g, '-').replace(/\\//g, '_').replace(/=+$/g, '');
                  }
                  function toProxyUrl(input) {
                    try {
                      const resolved = new URL(input, window.location.href);
                      if (resolved.pathname === '/api/proxy/web') return resolved.toString();
                      if (resolved.protocol !== 'http:' && resolved.protocol !== 'https:') return input;
                      return PROXY_PREFIX + toBase64Url(resolved.toString());
                    } catch (error) {
                      return input;
                    }
                  }
                  const originalFetch = window.fetch;
                  if (typeof originalFetch === 'function') {
                    window.fetch = function (input, init) {
                      if (typeof input === 'string') {
                        return originalFetch.call(this, toProxyUrl(input), init);
                      }
                      if (input && input.url) {
                        return originalFetch.call(this, toProxyUrl(input.url), init);
                      }
                      return originalFetch.call(this, input, init);
                    };
                  }
                  const originalOpen = XMLHttpRequest.prototype.open;
                  XMLHttpRequest.prototype.open = function (method, url) {
                    const args = Array.prototype.slice.call(arguments);
                    if (typeof url === 'string') {
                      args[1] = toProxyUrl(url);
                    }
                    return originalOpen.apply(this, args);
                  };
                })();
                """;
        document.head().appendElement("script").appendChild(new DataNode(patchScript));
    }

    private String resolveRedirectUrl(String location, URI requestUri) {
        if (location == null || location.isBlank()) {
            return null;
        }
        String resolvedLocation;
        if (location.startsWith("http://") || location.startsWith("https://")) {
            resolvedLocation = location;
        } else if (location.startsWith("?")) {
            resolvedLocation = currentDocumentBase(requestUri) + location;
        } else if (location.startsWith("#")) {
            resolvedLocation = currentDocumentUri(requestUri) + location;
        } else {
            resolvedLocation = requestUri.resolve(location).toString();
        }
        return WebProxyTargetValidator.normalizeAndValidate(resolvedLocation);
    }

    private static String resolveHtmlBaseUrl(URI requestUri) {
        String path = requestUri.getPath();
        String basePath = "";
        if (path != null && !path.isBlank() && !"/".equals(path)) {
            if (path.endsWith("/")) {
                basePath = path.substring(0, path.length() - 1);
            } else {
                int lastSlash = path.lastIndexOf('/');
                basePath = lastSlash > 0 ? path.substring(0, lastSlash) : "";
            }
        }
        String authority = requestUri.getRawAuthority();
        return requestUri.getScheme() + "://" + authority + basePath;
    }

    private static URI currentDocumentUri(URI requestUri) {
        String query = requestUri.getRawQuery();
        String currentDocument = currentDocumentBase(requestUri);
        if (query == null || query.isBlank()) {
            return URI.create(currentDocument);
        }
        return URI.create(currentDocument + "?" + query);
    }

    private static String currentDocumentBase(URI requestUri) {
        String authority = requestUri.getRawAuthority();
        String path = requestUri.getRawPath();
        StringBuilder builder = new StringBuilder(requestUri.getScheme())
                .append("://")
                .append(authority);
        if (path != null && !path.isBlank()) {
            builder.append(path);
        }
        return builder.toString();
    }

    private static String buildProxyRedirectUrl(String redirectUrl) {
        return "/api/proxy/web?url="
                + Base64.getUrlEncoder().withoutPadding().encodeToString(redirectUrl.getBytes(StandardCharsets.UTF_8));
    }
}
