package com.openmanus.infra.config;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Starts the local front/ Vite dev server with the Spring Boot web application.
 */
@Component
public class FrontendDevServerLifecycle {

  private static final Logger log = LoggerFactory.getLogger(FrontendDevServerLifecycle.class);
  private static final Duration PROBE_TIMEOUT = Duration.ofMillis(500);

  private final OpenManusProperties properties;
  private Process process;

  public FrontendDevServerLifecycle(OpenManusProperties properties) {
    this.properties = properties;
  }

  /**
   * Starts Vite after Spring is ready so the backend can proxy frontend requests on 8089.
   */
  @EventListener(ApplicationReadyEvent.class)
  public synchronized void start() {
    Path frontDir = Path.of(System.getProperty("user.dir"), "front");
    if (!Files.isRegularFile(frontDir.resolve("package.json"))) {
      log.info("Front directory not found, skipping frontend dev server startup: {}", frontDir);
      return;
    }
    URI devServerUri = resolveDevServerUri();
    if (devServerUri == null) {
      log.warn("Invalid frontend dev server URL: {}", properties.getFrontend().getDevServerUrl());
      return;
    }
    if (isDevServerReady(devServerUri)) {
      log.info("Frontend dev server already running at {}", devServerUri);
      return;
    }

    try {
      process = new ProcessBuilder(List.of(
          "npm", "run", "dev", "--",
          "--host", devServerUri.getHost(),
          "--port", Integer.toString(resolvePort(devServerUri)),
          "--strictPort"
      ))
          .directory(frontDir.toFile())
          .inheritIO()
          .start();
      log.info("Started frontend dev server from {} at {}", frontDir, devServerUri);
    } catch (IOException e) {
      log.warn("Failed to start frontend dev server from {}", frontDir, e);
    }
  }

  /**
   * Stops the child Vite process when the Spring context closes.
   */
  @EventListener(ContextClosedEvent.class)
  public synchronized void stop() {
    if (process == null || !process.isAlive()) {
      return;
    }
    process.destroy();
    log.info("Stopped frontend dev server");
  }

  private static boolean isDevServerReady(URI devServerUri) {
    try {
      HttpURLConnection connection = (HttpURLConnection) devServerUri
          .toURL()
          .openConnection();
      connection.setConnectTimeout((int) PROBE_TIMEOUT.toMillis());
      connection.setReadTimeout((int) PROBE_TIMEOUT.toMillis());
      connection.setRequestMethod("GET");
      return connection.getResponseCode() < 500;
    } catch (IOException e) {
      return false;
    }
  }

  private URI resolveDevServerUri() {
    String configured = properties.getFrontend().getDevServerUrl();
    if (configured == null || configured.isBlank()) {
      return URI.create("http://127.0.0.1:5173");
    }
    try {
      URI uri = URI.create(configured);
      if (uri.getScheme() == null || uri.getHost() == null) {
        return null;
      }
      return uri;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static int resolvePort(URI uri) {
    if (uri.getPort() > 0) {
      return uri.getPort();
    }
    return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
  }
}
