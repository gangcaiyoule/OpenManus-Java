package com.openmanus.infra.web;

import com.openmanus.domain.service.WebProxyConfigProvider;
import com.openmanus.domain.service.WebProxyResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpUrlConnectionWebProxyAdapterTest {

    private static final String PUBLIC_BASE_URL = "https://93.184.216.34";

    @Test
    void shouldInjectBaseTagAndFilterRestrictedHeadersForHtml() throws Exception {
        HttpUrlConnectionWebProxyAdapter adapter = adapterWith(new FakeHttpURLConnection(
                URI.create(PUBLIC_BASE_URL + "/html").toURL(),
                200,
                headers(
                        header("Content-Type", "text/html; charset=utf-8"),
                        header("X-Frame-Options", "DENY"),
                        header("Cache-Control", "no-cache")
                ),
                "<html><head><title>x</title></head><body>ok</body></html>".getBytes(StandardCharsets.UTF_8),
                null
        ));

        WebProxyResult result = adapter.fetch(PUBLIC_BASE_URL + "/html");

        String body = new String(result.body(), StandardCharsets.UTF_8);
        assertEquals(200, result.statusCode());
        assertTrue(body.contains("<base href=\"" + PUBLIC_BASE_URL + "/\" target=\"_blank\">"));
        assertEquals("no-cache", headerValue(result, "Cache-Control"));
        assertFalse(hasHeader(result, "X-Frame-Options"));
    }

    @Test
    void shouldUseDocumentDirectoryAsBaseTagForNestedHtmlPath() throws Exception {
        HttpUrlConnectionWebProxyAdapter adapter = adapterWith(new FakeHttpURLConnection(
                URI.create(PUBLIC_BASE_URL + "/docs/guide/page.html?lang=zh#intro").toURL(),
                200,
                headers(header("Content-Type", "text/html; charset=utf-8")),
                "<html><head><title>x</title></head><body>ok</body></html>".getBytes(StandardCharsets.UTF_8),
                null
        ));

        WebProxyResult result = adapter.fetch(PUBLIC_BASE_URL + "/docs/guide/page.html?lang=zh#intro");

        String body = new String(result.body(), StandardCharsets.UTF_8);
        assertTrue(body.contains("<base href=\"" + PUBLIC_BASE_URL + "/docs/guide/\" target=\"_blank\">"));
    }

    @Test
    void shouldKeepExistingBaseTagAndBinaryBody() throws Exception {
        HttpUrlConnectionWebProxyAdapter htmlAdapter = adapterWith(new FakeHttpURLConnection(
                URI.create(PUBLIC_BASE_URL + "/html-with-base").toURL(),
                200,
                headers(header("Content-Type", "text/html; charset=utf-8")),
                "<html><head><base href=\"https://example.com/\"></head><body></body></html>"
                        .getBytes(StandardCharsets.UTF_8),
                null
        ));
        HttpUrlConnectionWebProxyAdapter binaryAdapter = adapterWith(new FakeHttpURLConnection(
                URI.create(PUBLIC_BASE_URL + "/binary").toURL(),
                200,
                headers(header("Content-Type", "application/octet-stream")),
                new byte[]{1, 2, 3, 4},
                null
        ));

        WebProxyResult htmlResult = htmlAdapter.fetch(PUBLIC_BASE_URL + "/html-with-base");
        WebProxyResult binaryResult = binaryAdapter.fetch(PUBLIC_BASE_URL + "/binary");

        String htmlBody = new String(htmlResult.body(), StandardCharsets.UTF_8);
        assertEquals(1, htmlBody.split("<base ", -1).length - 1);
        assertArrayEquals(new byte[]{1, 2, 3, 4}, binaryResult.body());
    }

    @Test
    void shouldConvertRelativeRedirectToProxyRedirectUrl() throws Exception {
        HttpUrlConnectionWebProxyAdapter adapter = adapterWith(new FakeHttpURLConnection(
                URI.create(PUBLIC_BASE_URL + "/redirect").toURL(),
                302,
                headers(header("Location", "/next")),
                new byte[0],
                null
        ));

        WebProxyResult result = adapter.fetch(PUBLIC_BASE_URL + "/redirect");

        assertTrue(result.isRedirect());
        assertEquals(encodedRedirect(PUBLIC_BASE_URL + "/next"), result.redirectLocation());
    }

    @Test
    void shouldResolveRelativeRedirectAgainstFullRequestUri() throws Exception {
        HttpUrlConnectionWebProxyAdapter adapter = adapterWith(new FakeHttpURLConnection(
                URI.create(PUBLIC_BASE_URL + "/docs/guide/current/index.html?lang=zh").toURL(),
                302,
                headers(header("Location", "../next/page.html?mode=preview#summary")),
                new byte[0],
                null
        ));

        WebProxyResult result = adapter.fetch(PUBLIC_BASE_URL + "/docs/guide/current/index.html?lang=zh");

        String expectedTarget = PUBLIC_BASE_URL + "/docs/guide/next/page.html?mode=preview#summary";
        assertEquals(encodedRedirect(expectedTarget), result.redirectLocation());
    }

    @Test
    void shouldResolveQueryAndFragmentRedirectAgainstCurrentDocument() throws Exception {
        HttpUrlConnectionWebProxyAdapter queryAdapter = adapterWith(new FakeHttpURLConnection(
                URI.create(PUBLIC_BASE_URL + "/docs/query/page.html?lang=zh").toURL(),
                302,
                headers(header("Location", "?tab=files")),
                new byte[0],
                null
        ));
        HttpUrlConnectionWebProxyAdapter fragmentAdapter = adapterWith(new FakeHttpURLConnection(
                URI.create(PUBLIC_BASE_URL + "/docs/fragment/page.html?lang=zh").toURL(),
                302,
                headers(header("Location", "#details")),
                new byte[0],
                null
        ));

        WebProxyResult queryResult = queryAdapter.fetch(PUBLIC_BASE_URL + "/docs/query/page.html?lang=zh");
        WebProxyResult fragmentResult = fragmentAdapter.fetch(PUBLIC_BASE_URL + "/docs/fragment/page.html?lang=zh");

        assertEquals(encodedRedirect(PUBLIC_BASE_URL + "/docs/query/page.html?tab=files"), queryResult.redirectLocation());
        assertEquals(encodedRedirect(PUBLIC_BASE_URL + "/docs/fragment/page.html?lang=zh#details"), fragmentResult.redirectLocation());
    }

    @Test
    void shouldReadErrorBodyAndDefaultMissingContentType() throws Exception {
        HttpUrlConnectionWebProxyAdapter adapter = adapterWith(new FakeHttpURLConnection(
                URI.create(PUBLIC_BASE_URL + "/error").toURL(),
                502,
                headers(),
                null,
                "error-body".getBytes(StandardCharsets.UTF_8)
        ));

        WebProxyResult result = adapter.fetch(PUBLIC_BASE_URL + "/error");

        assertEquals(502, result.statusCode());
        assertEquals("text/html; charset=utf-8", result.contentType());
        assertTrue(new String(result.body(), StandardCharsets.UTF_8).contains("error-body"));
    }

    @Test
    void shouldIgnoreNullAndEmptyHeaderValues() throws Exception {
        Map<String, List<String>> responseHeaders = new LinkedHashMap<>();
        List<String> cacheControlValues = new ArrayList<>();
        cacheControlValues.add("no-cache");
        cacheControlValues.add(null);
        cacheControlValues.add("max-age=60");
        responseHeaders.put("Cache-Control", cacheControlValues);
        responseHeaders.put("X-Empty", new ArrayList<>());
        responseHeaders.put("X-Null", null);
        HttpUrlConnectionWebProxyAdapter adapter = adapterWith(new FakeHttpURLConnection(
                URI.create(PUBLIC_BASE_URL + "/headers").toURL(),
                200,
                responseHeaders,
                "ok".getBytes(StandardCharsets.UTF_8),
                null
        ));

        WebProxyResult result = adapter.fetch(PUBLIC_BASE_URL + "/headers");

        assertEquals(List.of("no-cache", "max-age=60"), result.headers().get("Cache-Control"));
        assertFalse(result.headers().containsKey("X-Empty"));
        assertFalse(result.headers().containsKey("X-Null"));
    }

    @Test
    void shouldTruncateOversizedBodyAtConfiguredLimit() throws Exception {
        byte[] oversized = new byte[HttpUrlConnectionWebProxyAdapter.MAX_CONTENT_SIZE + 1024];
        for (int i = 0; i < oversized.length; i++) {
            oversized[i] = 'a';
        }
        HttpUrlConnectionWebProxyAdapter adapter = adapterWith(new FakeHttpURLConnection(
                URI.create(PUBLIC_BASE_URL + "/large").toURL(),
                200,
                headers(header("Content-Type", "application/octet-stream")),
                oversized,
                null
        ));

        WebProxyResult result = adapter.fetch(PUBLIC_BASE_URL + "/large");

        assertEquals(HttpUrlConnectionWebProxyAdapter.MAX_CONTENT_SIZE, result.body().length);
    }

    @Test
    void shouldResolveProxyFromConfigBranches() {
        HttpUrlConnectionWebProxyAdapter httpsProxy =
                new HttpUrlConnectionWebProxyAdapter(proxyConfig(true, "http://127.0.0.1:8080", "http://10.0.0.1:8443"));
        HttpUrlConnectionWebProxyAdapter httpFallback =
                new HttpUrlConnectionWebProxyAdapter(proxyConfig(true, "127.0.0.1:8080", ""));
        HttpUrlConnectionWebProxyAdapter malformed =
                new HttpUrlConnectionWebProxyAdapter(proxyConfig(true, "bad-proxy", ""));
        HttpUrlConnectionWebProxyAdapter disabled =
                new HttpUrlConnectionWebProxyAdapter(proxyConfig(false, "127.0.0.1:8080", "127.0.0.1:8443"));

        Proxy https = httpsProxy.resolveProxy();
        Proxy fallback = httpFallback.resolveProxy();

        assertNotNull(https);
        assertNotNull(fallback);
        assertNull(malformed.resolveProxy());
        assertNull(disabled.resolveProxy());
    }

    @Test
    void shouldRejectLoopbackTargetBeforeOpeningConnection() {
        HttpUrlConnectionWebProxyAdapter adapter = new HttpUrlConnectionWebProxyAdapter(proxyConfig(false, "", "")) {
            @Override
            HttpURLConnection openConnection(String urlString) {
                throw new AssertionError("connection must not be opened for rejected target");
            }
        };

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> adapter.fetch("http://127.0.0.1:8080/admin"));

        assertTrue(exception.getMessage().contains("not allowed"));
    }

    @Test
    void shouldRejectUnsupportedSchemeBeforeOpeningConnection() {
        HttpUrlConnectionWebProxyAdapter adapter = new HttpUrlConnectionWebProxyAdapter(proxyConfig(false, "", "")) {
            @Override
            HttpURLConnection openConnection(String urlString) {
                throw new AssertionError("connection must not be opened for rejected target");
            }
        };

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> adapter.fetch("ftp://93.184.216.34/file.txt"));

        assertEquals("Target URL must use http or https", exception.getMessage());
    }

    @Test
    void shouldExposeStandaloneNormalizationEntry() {
        HttpUrlConnectionWebProxyAdapter adapter =
                new HttpUrlConnectionWebProxyAdapter(proxyConfig(false, "", ""));

        String normalized = adapter.normalizeTargetUrl(PUBLIC_BASE_URL + "/docs/../guide");

        assertEquals(PUBLIC_BASE_URL + "/guide", normalized);
    }

    @Test
    void shouldRejectRedirectToBlockedTarget() throws Exception {
        HttpUrlConnectionWebProxyAdapter adapter = adapterWith(new FakeHttpURLConnection(
                URI.create(PUBLIC_BASE_URL + "/redirect").toURL(),
                302,
                headers(header("Location", "http://127.0.0.1:8080/private")),
                new byte[0],
                null
        ));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> adapter.fetch(PUBLIC_BASE_URL + "/redirect"));

        assertTrue(exception.getMessage().contains("not allowed"));
    }

    private static HttpUrlConnectionWebProxyAdapter adapterWith(FakeHttpURLConnection connection) {
        return new HttpUrlConnectionWebProxyAdapter(proxyConfig(false, "", "")) {
            @Override
            HttpURLConnection openConnection(String urlString) {
                return connection;
            }
        };
    }

    private static WebProxyConfigProvider proxyConfig(boolean enabled, String httpProxy, String httpsProxy) {
        return new WebProxyConfigProvider() {
            @Override
            public boolean enabled() {
                return enabled;
            }

            @Override
            public String httpProxy() {
                return httpProxy;
            }

            @Override
            public String httpsProxy() {
                return httpsProxy;
            }
        };
    }

    private static Map<String, List<String>> headers(Map.Entry<String, List<String>>... entries) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : entries) {
            headers.put(entry.getKey(), entry.getValue());
        }
        return headers;
    }

    private static Map.Entry<String, List<String>> header(String name, String value) {
        return Map.entry(name, List.of(value));
    }

    private static String encodedRedirect(String targetUrl) {
        return "/api/proxy/web?url="
                + java.util.Base64.getUrlEncoder().encodeToString(targetUrl.getBytes(StandardCharsets.UTF_8));
    }

    private static String headerValue(WebProxyResult result, String name) {
        return result.headers().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(entry -> entry.getValue().getFirst())
                .findFirst()
                .orElse(null);
    }

    private static boolean hasHeader(WebProxyResult result, String name) {
        return result.headers().keySet().stream().anyMatch(key -> key.equalsIgnoreCase(name));
    }

    private static final class FakeHttpURLConnection extends HttpURLConnection {
        private final Map<String, List<String>> headers;
        private final byte[] responseBody;
        private final byte[] errorBody;

        private FakeHttpURLConnection(URL url,
                                      int responseCode,
                                      Map<String, List<String>> headers,
                                      byte[] responseBody,
                                      byte[] errorBody) {
            super(url);
            this.responseCode = responseCode;
            this.headers = headers;
            this.responseBody = responseBody;
            this.errorBody = errorBody;
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
        }

        @Override
        public InputStream getInputStream() throws IOException {
            if (responseCode >= 400) {
                throw new IOException("HTTP " + responseCode);
            }
            return new ByteArrayInputStream(responseBody == null ? new byte[0] : responseBody);
        }

        @Override
        public InputStream getErrorStream() {
            return errorBody == null ? null : new ByteArrayInputStream(errorBody);
        }

        @Override
        public int getResponseCode() {
            return responseCode;
        }

        @Override
        public String getContentType() {
            return getHeaderField("Content-Type");
        }

        @Override
        public String getHeaderField(String name) {
            List<String> values = headers.get(name);
            return values == null || values.isEmpty() ? null : values.getFirst();
        }

        @Override
        public Map<String, List<String>> getHeaderFields() {
            return headers;
        }
    }
}
