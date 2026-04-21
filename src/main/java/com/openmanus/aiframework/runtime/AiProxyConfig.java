package com.openmanus.aiframework.runtime;

/**
 * Runtime abstraction for outbound HTTP proxy settings.
 */
public interface AiProxyConfig {
    boolean enabled();

    String httpProxy();

    String httpsProxy();
}
