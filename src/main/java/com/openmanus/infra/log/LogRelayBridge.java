package com.openmanus.infra.log;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Static bridge used by non-Spring appenders to access Spring-managed LogRelayService.
 */
public final class LogRelayBridge {

  private static final AtomicReference<LogRelayService> SERVICE_REF = new AtomicReference<>();

  private LogRelayBridge() {
  }

  public static void register(LogRelayService service) {
    SERVICE_REF.set(service);
  }

  public static LogRelayService get() {
    return SERVICE_REF.get();
  }
}
