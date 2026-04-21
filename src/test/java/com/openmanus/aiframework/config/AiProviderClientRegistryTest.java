package com.openmanus.aiframework.config;

import com.openmanus.aiframework.api.AiProviderClient;
import com.openmanus.aiframework.api.StreamListener;
import com.openmanus.aiframework.model.AiProviderType;
import com.openmanus.aiframework.model.ChatRequestEnvelope;
import com.openmanus.aiframework.model.ChatResponseEnvelope;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiProviderClientRegistryTest {

    @Test
    void shouldAllowConstructingWithEmptyMap() {
        AiProviderClientRegistry registry = new AiProviderClientRegistry(Map.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> registry.getClient(AiProviderType.OPENAI));
        assertEquals("No client registered for provider: OPENAI", ex.getMessage());
    }

    @Test
    void shouldReturnRegisteredClient() {
        AiProviderClient openAi = new NoopClient();
        AiProviderClientRegistry registry = new AiProviderClientRegistry(Map.of(AiProviderType.OPENAI, openAi));

        assertEquals(openAi, registry.getClient(AiProviderType.OPENAI));
    }

    private static class NoopClient implements AiProviderClient {
        @Override
        public ChatResponseEnvelope chat(ChatRequestEnvelope request) {
            return ChatResponseEnvelope.builder().build();
        }

        @Override
        public void streamChat(ChatRequestEnvelope request, StreamListener listener) {
            // no-op
        }
    }
}
