package com.openmanus.aiframework.runtime;

/**
 * Runtime abstraction for search configuration used by tools.
 */
public interface AiSearchConfig {
    String apiKey();

    int maxResults();

    String serperEndpoint();
}
