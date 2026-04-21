package com.openmanus.domain.service;

import java.io.IOException;

public interface WebProxyFetchPort {

    WebProxyResult fetch(String targetUrl) throws IOException;

    String normalizeTargetUrl(String targetUrl);
}
