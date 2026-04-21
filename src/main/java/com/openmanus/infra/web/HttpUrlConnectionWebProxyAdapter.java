package com.openmanus.infra.web;

import com.openmanus.domain.service.WebProxyConfigProvider;
import com.openmanus.domain.service.WebProxyFetchPort;
import com.openmanus.domain.service.WebProxyResult;
import lombok.extern.slf4j.Slf4j;
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
                    String processed = processHtmlContent(new String(body, StandardCharsets.UTF_8), resolveHtmlBaseUrl(requestUri));
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

    private static String processHtmlContent(String content, String baseUrl) {
        if (content.toLowerCase().contains("<base ")) {
            return content;
        }
        String baseTag = "<base href=\"" + baseUrl + "/\" target=\"_blank\">";
        int headIndex = content.toLowerCase().indexOf("<head>");
        if (headIndex != -1) {
            int insertPos = headIndex + 6;
            return content.substring(0, insertPos) + "\n" + baseTag + "\n" + content.substring(insertPos);
        }
        int htmlIndex = content.toLowerCase().indexOf("<html");
        if (htmlIndex != -1) {
            int closeIndex = content.indexOf(">", htmlIndex);
            if (closeIndex != -1) {
                return content.substring(0, closeIndex + 1)
                        + "\n<head>" + baseTag + "</head>\n"
                        + content.substring(closeIndex + 1);
            }
        }
        return content;
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
        byte[] encoded = java.util.Base64.getUrlEncoder().encode(redirectUrl.getBytes(StandardCharsets.UTF_8));
        return "/api/proxy/web?url=" + new String(encoded, StandardCharsets.UTF_8);
    }
}
