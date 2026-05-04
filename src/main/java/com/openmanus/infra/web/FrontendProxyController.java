package com.openmanus.infra.web;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Proxies frontend page requests to the Vite dev server.
 */
@Controller
public class FrontendProxyController {

  private static final String DEV_SERVER_URL = "http://127.0.0.1:5173";
  private static final Duration PROXY_TIMEOUT = Duration.ofSeconds(5);

  private final HttpClient httpClient = HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build();

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

    try {
      HttpRequest proxyRequest = HttpRequest.newBuilder()
          .uri(URI.create(targetUrl(path, request.getQueryString())))
          .version(HttpClient.Version.HTTP_1_1)
          .timeout(PROXY_TIMEOUT)
          .header("Accept", request.getHeader("Accept") == null ? "*/*" : request.getHeader("Accept"))
          .GET()
          .build();
      HttpResponse<byte[]> response = httpClient.send(proxyRequest,
          HttpResponse.BodyHandlers.ofByteArray());
      return ResponseEntity.status(response.statusCode())
          .headers(toHeaders(response))
          .body(response.body());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    } catch (IOException | IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }
  }

  private static HttpHeaders toHeaders(HttpResponse<byte[]> response) {
    HttpHeaders headers = new HttpHeaders();
    response.headers().firstValue("Content-Type").ifPresent(value -> headers.set("Content-Type", value));
    return headers;
  }

  private static String targetUrl(String path, String queryString) {
    String url = DEV_SERVER_URL + (path.isEmpty() ? "/" : "/" + path);
    if (queryString != null && !queryString.isEmpty()) {
      return url + "?" + queryString;
    }
    return url;
  }

  private static String normalizePath(String requestUri) {
    if (requestUri == null || requestUri.isBlank() || "/".equals(requestUri)) {
      return "";
    }
    return requestUri.startsWith("/") ? requestUri.substring(1) : requestUri;
  }
}
