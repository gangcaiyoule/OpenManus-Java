package com.openmanus.infra.config;

import com.openmanus.domain.service.LegacySessionMappingPolicy;
import org.springframework.stereotype.Component;

/**
 * Infra adapter exposing legacy mapping policy to runtime/domain layers.
 */
@Component
public class RuntimeLegacyMappingPolicyAdapter implements LegacySessionMappingPolicy {

    private final OpenManusProperties properties;

    public RuntimeLegacyMappingPolicyAdapter(OpenManusProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean warnEnabled() {
        OpenManusProperties.LegacyMappingConfig config = properties.getLegacyMapping();
        return config != null && config.isWarnEnabled();
    }

    @Override
    public int warnSampleRate() {
        OpenManusProperties.LegacyMappingConfig config = properties.getLegacyMapping();
        return config == null ? 200 : config.getWarnSampleRate();
    }
}
