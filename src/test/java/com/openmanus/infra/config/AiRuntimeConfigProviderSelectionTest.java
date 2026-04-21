package com.openmanus.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.aiframework.api.AiProviderClient;
import com.openmanus.aiframework.api.StreamListener;
import com.openmanus.aiframework.config.AiProviderClientRegistry;
import com.openmanus.aiframework.model.AiProviderType;
import com.openmanus.aiframework.model.ChatRequestEnvelope;
import com.openmanus.aiframework.model.ChatResponseEnvelope;
import com.openmanus.aiframework.runtime.AiChatModel;
import com.openmanus.aiframework.runtime.model.AiChatMessage;
import com.openmanus.aiframework.runtime.model.AiChatRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiRuntimeConfigProviderSelectionTest {

    @Test
    void shouldUseConfiguredDefaultProvider() {
        OpenManusProperties properties = new OpenManusProperties();
        properties.getLlm().getDefaultLlm().setApiType("anthropic");
        properties.getLlm().getDefaultLlm().setModel("claude-3-5-sonnet-latest");

        CapturingClient openAi = new CapturingClient(AiProviderType.OPENAI);
        CapturingClient anthropic = new CapturingClient(AiProviderType.ANTHROPIC);
        CapturingClient gemini = new CapturingClient(AiProviderType.GEMINI);

        AiRuntimeConfig config = new AiRuntimeConfig(properties);
        AiChatModel model = config.chatModel(
                new AiProviderClientRegistry(Map.of(
                        AiProviderType.OPENAI, openAi,
                        AiProviderType.ANTHROPIC, anthropic,
                        AiProviderType.GEMINI, gemini
                )),
                new ObjectMapper()
        );

        model.chat(new AiChatRequest("", List.of(AiChatMessage.user("hello")), List.of(), null, null, null, null));

        assertEquals(0, openAi.callCount);
        assertEquals(1, anthropic.callCount);
        assertEquals(0, gemini.callCount);
    }

    @Test
    void shouldFallbackToOpenAiWhenProviderTypeUnknown() {
        OpenManusProperties properties = new OpenManusProperties();
        properties.getLlm().getDefaultLlm().setApiType("unknown");

        CapturingClient openAi = new CapturingClient(AiProviderType.OPENAI);
        CapturingClient anthropic = new CapturingClient(AiProviderType.ANTHROPIC);
        CapturingClient gemini = new CapturingClient(AiProviderType.GEMINI);

        AiRuntimeConfig config = new AiRuntimeConfig(properties);
        AiChatModel model = config.chatModel(
                new AiProviderClientRegistry(Map.of(
                        AiProviderType.OPENAI, openAi,
                        AiProviderType.ANTHROPIC, anthropic,
                        AiProviderType.GEMINI, gemini
                )),
                new ObjectMapper()
        );

        model.chat(new AiChatRequest("", List.of(AiChatMessage.user("hello")), List.of(), null, null, null, null));

        assertEquals(1, openAi.callCount);
        assertEquals(0, anthropic.callCount);
        assertEquals(0, gemini.callCount);
    }

    private static class CapturingClient implements AiProviderClient {

        private final AiProviderType providerType;
        private int callCount;

        private CapturingClient(AiProviderType providerType) {
            this.providerType = providerType;
        }

        @Override
        public ChatResponseEnvelope chat(ChatRequestEnvelope request) {
            callCount++;
            return ChatResponseEnvelope.builder()
                    .providerType(providerType)
                    .content("ok")
                    .build();
        }

        @Override
        public void streamChat(ChatRequestEnvelope request, StreamListener listener) {
            throw new UnsupportedOperationException("not used");
        }
    }
}
