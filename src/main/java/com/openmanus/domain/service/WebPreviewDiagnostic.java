package com.openmanus.domain.service;

public record WebPreviewDiagnostic(
        boolean enabled,
        String targetUrl,
        String proxyUrl,
        String state,
        String reasonCode,
        String reason,
        String previewMode,
        String redirectLocation,
        String contentType,
        boolean previewableHtml,
        boolean fallbackToVnc
) {
}
