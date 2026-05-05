package com.openmanus.infra.web;

import com.openmanus.infra.config.OpenManusProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Proxies frontend page requests to the Vite dev server and falls back to packaged front/dist assets.
 */
@Controller
public class FrontendProxyController {

  private static final Duration PROXY_TIMEOUT = Duration.ofSeconds(5);

  private final HttpClient httpClient = HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build();

  private final OpenManusProperties properties;
  private final ResourceLoader resourceLoader;

  public FrontendProxyController(OpenManusProperties properties, ResourceLoader resourceLoader) {
    this.properties = properties;
    this.resourceLoader = resourceLoader;
  }

  /**
   * Proxies non-backend GET requests so the frontend opens through the backend port.
   */
  @GetMapping({
      "",
      "/",
      "/{first:^(?!api$|ws$|actuator$|swagger-ui$|v3$|webjars$).*$}/**"
  })
  public ResponseEntity<byte[]> proxy(HttpServletRequest request) {
    String path = normalizePath(request.getRequestURI());
    ResponseEntity<byte[]> proxied = tryProxy(path, request.getQueryString(), request.getHeader("Accept"));
    if (proxied != null) {
      return proxied;
    }
    return servePackagedAsset(path);
  }

  private ResponseEntity<byte[]> tryProxy(String path, String queryString, String acceptHeader) {
    try {
      HttpRequest proxyRequest = HttpRequest.newBuilder()
          .uri(URI.create(targetUrl(path, queryString)))
          .version(HttpClient.Version.HTTP_1_1)
          .timeout(PROXY_TIMEOUT)
          .header("Accept", acceptHeader == null ? "*/*" : acceptHeader)
          .GET()
          .build();
      HttpResponse<byte[]> response = httpClient.send(proxyRequest, HttpResponse.BodyHandlers.ofByteArray());
      return ResponseEntity.status(response.statusCode())
          .headers(copyHeaders(response))
          .body(response.body());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    } catch (ConnectException e) {
      return null;
    } catch (IOException | IllegalArgumentException e) {
      return null;
    }
  }

  private ResponseEntity<byte[]> servePackagedAsset(String path) {
    try {
      String normalized = normalizeResourcePath(path);
      Resource resource = resolveResource(normalized);
      if (resource == null || !resource.exists()) {
        return unavailablePage();
      }
      byte[] content = resource.getInputStream().readAllBytes();
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(detectMediaType(normalized));
      return ResponseEntity.ok().headers(headers).body(content);
    } catch (IOException e) {
      return unavailablePage();
    }
  }

  private Resource resolveResource(String normalizedPath) throws IOException {
    Resource direct = resourceLoader.getResource("classpath:static/" + normalizedPath);
    if (direct.exists() && direct.isReadable()) {
      return direct;
    }
    Resource spaIndex = resourceLoader.getResource("classpath:static/index.html");
    if (spaIndex.exists() && spaIndex.isReadable()) {
      return spaIndex;
    }
    return null;
  }

  private ResponseEntity<byte[]> unavailablePage() {
    String devServerUrl = properties.getFrontend().getDevServerUrl();
    String body = """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>OpenManus Frontend Unavailable</title>
          <style>
            body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 0; background: #f5f7fb; color: #1f2937; }
            main { max-width: 760px; margin: 64px auto; background: white; padding: 32px; border-radius: 16px; box-shadow: 0 18px 48px rgba(15, 23, 42, 0.08); }
            code { background: #eef2ff; padding: 2px 6px; border-radius: 6px; }
          </style>
        </head>
        <body>
          <main>
            <h1>Frontend unavailable</h1>
            <p>The backend is running, but no frontend bundle or dev server is currently reachable.</p>
            <p>Expected dev server: <code>%s</code></p>
            <p>To recover, either start <code>front/</code> with <code>npm run dev</code> or build the frontend so packaged assets are available.</p>
          </main>
        </body>
        </html>
        """.formatted(devServerUrl);
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .contentType(MediaType.TEXT_HTML)
        .body(body.getBytes(StandardCharsets.UTF_8));
  }

  private HttpHeaders copyHeaders(HttpResponse<byte[]> response) {
    HttpHeaders headers = new HttpHeaders();
    response.headers().firstValue("Content-Type").ifPresent(value -> headers.set("Content-Type", value));
    return headers;
  }

  private String targetUrl(String path, String queryString) {
    String baseUrl = trimTrailingSlash(properties.getFrontend().getDevServerUrl());
    String url = baseUrl + (path.isEmpty() ? "/" : "/" + path);
    if (queryString != null && !queryString.isEmpty()) {
      return url + "?" + queryString;
    }
    return url;
  }

  private static String trimTrailingSlash(String value) {
    if (value == null || value.isBlank()) {
      return "http://127.0.0.1:5173";
    }
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private static MediaType detectMediaType(String path) {
    Optional<MediaType> mediaType = MediaTypeFactory.getMediaType(path);
    return mediaType.orElse(MediaType.APPLICATION_OCTET_STREAM);
  }

  private static String normalizePath(String requestUri) {
    if (requestUri == null || requestUri.isBlank() || "/".equals(requestUri)) {
      return "";
    }
    return requestUri.startsWith("/") ? requestUri.substring(1) : requestUri;
  }

  private static String normalizeResourcePath(String path) {
    if (path == null || path.isBlank()) {
      return "index.html";
    }
    String normalized = path.replace('\\', '/');
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    if (normalized.contains("..")) {
      return "index.html";
    }
    return normalized;
  }
}
