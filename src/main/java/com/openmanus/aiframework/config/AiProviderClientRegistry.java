package com.openmanus.aiframework.config;

import com.openmanus.aiframework.api.AiProviderClient;
import com.openmanus.aiframework.model.AiProviderType;

import java.util.EnumMap;
import java.util.Map;

public class AiProviderClientRegistry {

    private final Map<AiProviderType, AiProviderClient> clients;

    public AiProviderClientRegistry(Map<AiProviderType, AiProviderClient> clients) {
        EnumMap<AiProviderType, AiProviderClient> map = new EnumMap<>(AiProviderType.class);
        if (clients != null) {
            map.putAll(clients);
        }
        this.clients = map;
    }

    public AiProviderClient getClient(AiProviderType providerType) {
        AiProviderClient client = clients.get(providerType);
        if (client == null) {
            throw new IllegalArgumentException("No client registered for provider: " + providerType);
        }
        return client;
    }

    public Map<AiProviderType, AiProviderClient> all() {
        return Map.copyOf(clients);
    }
}
