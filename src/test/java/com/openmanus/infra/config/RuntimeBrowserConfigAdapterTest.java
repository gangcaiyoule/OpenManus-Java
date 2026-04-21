package com.openmanus.infra.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeBrowserConfigAdapterTest {

    @Test
    void shouldUseDefaultsWhenSearchAndProxyConfigMissing() {
        OpenManusProperties properties = new OpenManusProperties();
        properties.setSearch(null);
        properties.setProxy(null);
        RuntimeBrowserConfigAdapter adapter = new RuntimeBrowserConfigAdapter(properties);

        assertEquals("", adapter.apiKey());
        assertEquals(10, adapter.maxResults());
        assertEquals("https://google.serper.dev/search", adapter.serperEndpoint());
        assertFalse(adapter.enabled());
        assertEquals("", adapter.httpProxy());
        assertEquals("", adapter.httpsProxy());
    }

    @Test
    void shouldExposeConfiguredSearchAndProxySettings() {
        OpenManusProperties properties = new OpenManusProperties();
        OpenManusProperties.SearchConfig search = new OpenManusProperties.SearchConfig();
        search.setApiKey("  sk-test  ");
        search.setMaxResults(7);
        search.setSerperEndpoint(" https://custom.serper.dev/search ");
        properties.setSearch(search);
        OpenManusProperties.ProxyConfig proxy = new OpenManusProperties.ProxyConfig();
        proxy.setEnabled(true);
        proxy.setHttpProxy(" http://127.0.0.1:8080 ");
        proxy.setHttpsProxy(" http://127.0.0.1:8443 ");
        properties.setProxy(proxy);
        RuntimeBrowserConfigAdapter adapter = new RuntimeBrowserConfigAdapter(properties);

        assertEquals("sk-test", adapter.apiKey());
        assertEquals(7, adapter.maxResults());
        assertEquals("https://custom.serper.dev/search", adapter.serperEndpoint());
        assertTrue(adapter.enabled());
        assertEquals("http://127.0.0.1:8080", adapter.httpProxy());
        assertEquals("http://127.0.0.1:8443", adapter.httpsProxy());
    }
}
