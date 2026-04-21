package com.openmanus.domain.service;

public interface LegacySessionMappingPolicy {

    boolean warnEnabled();

    int warnSampleRate();
}
