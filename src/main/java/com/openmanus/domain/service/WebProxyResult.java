package com.openmanus.domain.service;

import java.util.List;
import java.util.Map;

public record WebProxyResult(
        int statusCode,
        String contentType,
        Map<String, List<String>> headers,
        byte[] body,
        String redirectLocation) {

    public boolean isRedirect() {
        return redirectLocation != null && !redirectLocation.isBlank();
    }

    public boolean isHtml() {
        return contentType != null && contentType.toLowerCase().contains("text/html");
    }
}
