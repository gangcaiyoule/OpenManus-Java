package com.openmanus.domain.service;

public interface WebProxyConfigProvider {

    boolean enabled();

    String httpProxy();

    String httpsProxy();
}
