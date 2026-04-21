package com.openmanus.domain.service;

import java.io.IOException;

public class WebProxyService {

    private final WebProxyFetchPort webProxyFetchPort;

    public WebProxyService(WebProxyFetchPort webProxyFetchPort) {
        this.webProxyFetchPort = webProxyFetchPort;
    }

    public WebProxyResult fetch(String targetUrl) throws IOException {
        return webProxyFetchPort.fetch(targetUrl);
    }

    public String validateTargetUrl(String targetUrl) {
        return webProxyFetchPort.normalizeTargetUrl(targetUrl);
    }
}
