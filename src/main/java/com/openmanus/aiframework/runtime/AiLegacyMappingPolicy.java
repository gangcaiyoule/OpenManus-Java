package com.openmanus.aiframework.runtime;

/**
 * Runtime abstraction for legacy session-id mapping log policy.
 */
public interface AiLegacyMappingPolicy {
    boolean warnEnabled();

    int warnSampleRate();
}
