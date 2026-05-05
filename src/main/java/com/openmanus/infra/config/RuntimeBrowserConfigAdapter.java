package com.openmanus.infra.config;

import com.openmanus.aiframework.runtime.AiProxyConfig;
import com.openmanus.aiframework.runtime.AiSearchConfig;
import com.openmanus.domain.service.WebProxyConfigProvider;
import org.springframework.stereotype.Component;

/**
 * Infra adapter that exposes browser-related configs as runtime ports.
 */
@Component
public class RuntimeBrowserConfigAdapter implements AiSearchConfig, AiProxyConfig, WebProxyConfigProvider {

    private static final String DEFAULT_SERPER_ENDPOINT = "https://google.serper.dev/search";
    private static final int DEFAULT_SEARCH_MAX_RESULTS = 10;

    private final OpenManusProperties properties;

    public RuntimeBrowserConfigAdapter(OpenManusProperties properties) {
        this.properties = properties;
    }

    @Override
    public String apiKey() {
        OpenManusProperties.SearchConfig cfg = properties.getSearch();
        return cfg == null ? "" : safe(cfg.getApiKey());
    }

    @Override
    public int maxResults() {
        OpenManusProperties.SearchConfig cfg = properties.getSearch();
        if (cfg == null) {
            return DEFAULT_SEARCH_MAX_RESULTS;
        }
        return cfg.getMaxResults();
    }

    @Override
    public String serperEndpoint() {
        OpenManusProperties.SearchConfig cfg = properties.getSearch();
        String endpoint = cfg == null ? "" : safe(cfg.getSerperEndpoint());
        return endpoint.isEmpty() ? DEFAULT_SERPER_ENDPOINT : endpoint;
    }

    @Override
    public boolean enabled() {
        OpenManusProperties.ProxyConfig cfg = properties.getProxy();
        return cfg != null && cfg.isEnabled();
    }

    @Override
    public String httpProxy() {
        OpenManusProperties.ProxyConfig cfg = properties.getProxy();
        return cfg == null ? "" : safe(cfg.getHttpProxy());
    }

    @Override
    public String httpsProxy() {
        OpenManusProperties.ProxyConfig cfg = properties.getProxy();
        return cfg == null ? "" : safe(cfg.getHttpsProxy());
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
