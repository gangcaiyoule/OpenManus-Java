package com.openmanus.infra.web;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

public final class WebProxyTargetValidator {

    private WebProxyTargetValidator() {
    }

    public static String normalizeAndValidate(String targetUrl) {
        if (targetUrl == null || targetUrl.isBlank()) {
            throw new IllegalArgumentException("Target URL must not be blank");
        }
        String trimmed = targetUrl.trim();
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Target URL is invalid", ex);
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Target URL must use http or https");
        }
        if (!uri.isAbsolute()) {
            throw new IllegalArgumentException("Target URL must be absolute");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Target URL host must not be blank");
        }
        if ("localhost".equalsIgnoreCase(host) || host.toLowerCase().endsWith(".localhost")) {
            throw new IllegalArgumentException("Target URL host is not allowed");
        }
        rejectLocalAddress(host);
        return uri.normalize().toString();
    }

    private static void rejectLocalAddress(String host) {
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isSiteLocalAddress()
                        || address.isLinkLocalAddress()
                        || isUniqueLocalIpv6(address)) {
                    throw new IllegalArgumentException("Target URL host is not allowed");
                }
            }
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("Target URL host is not resolvable", ex);
        }
    }

    private static boolean isUniqueLocalIpv6(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }
        byte firstByte = address.getAddress()[0];
        return (firstByte & (byte) 0xfe) == (byte) 0xfc;
    }
}
