package com.openmanus.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.aiframework.api.AiProviderClient;
import com.openmanus.aiframework.api.StreamListener;
import com.openmanus.aiframework.config.AiProviderClientRegistry;
import com.openmanus.aiframework.model.AiProviderType;
import com.openmanus.aiframework.model.ChatRequestEnvelope;
import com.openmanus.aiframework.model.ChatResponseEnvelope;
import com.openmanus.aiframework.runtime.AiChatModel;
import com.openmanus.aiframework.runtime.AiMemoryStore;
import com.openmanus.aiframework.runtime.AiToolResultArtifactStore;
import com.openmanus.aiframework.runtime.model.AiChatMessage;
import com.openmanus.aiframework.runtime.model.AiChatRequest;
import com.openmanus.infra.memory.FileChatMemoryStore;
import com.openmanus.infra.memory.FileToolResultArtifactStore;
import com.openmanus.infra.memory.InMemoryAiMemoryStore;
import com.openmanus.infra.memory.InMemoryToolResultArtifactStore;
import com.openmanus.infra.memory.PersistentAiMemory;
import com.openmanus.infra.memory.ToolResultArtifactStore;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRuntimeConfigRuntimeWiringTest {

    @Test
    void chatMemoryProviderShouldCreatePersistentAiMemory() {
        OpenManusProperties properties = new OpenManusProperties();
        AiRuntimeConfig config = new AiRuntimeConfig(properties);
        AiMemoryStore store = new InMemoryAiMemoryStore();

        assertInstanceOf(PersistentAiMemory.class, config.chatMemoryProvider(store).get("conv-id"));
    }

    @Test
    void chatMemoryStoreShouldUseInMemoryAlias() {
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setStoreType("InMemory");
        AiRuntimeConfig config = new AiRuntimeConfig(properties);

        assertInstanceOf(InMemoryAiMemoryStore.class, config.chatMemoryStore());
    }

    @Test
    void chatMemoryStoreShouldParseInMemoryAliasUnderTurkishLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            OpenManusProperties properties = new OpenManusProperties();
            properties.getChatMemory().setStoreType("InMemory");
            AiRuntimeConfig config = new AiRuntimeConfig(properties);

            assertInstanceOf(InMemoryAiMemoryStore.class, config.chatMemoryStore());
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void chatMemoryStoreShouldUseFileStoreByDefaultWhenStoreTypeIsBlank() throws Exception {
        Path dir = Files.createTempDirectory("lc4j-config-file-default-");
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setStoreType("  ");
        properties.getChatMemory().setFileStoreDir(dir.toString());
        AiRuntimeConfig config = new AiRuntimeConfig(properties);

        assertInstanceOf(FileChatMemoryStore.class, config.chatMemoryStore());
    }

    @Test
    void chatMemoryStoreShouldUseFileStoreWhenStoreTypeIsNull() throws Exception {
        Path dir = Files.createTempDirectory("lc4j-config-file-default-null-");
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setStoreType(null);
        properties.getChatMemory().setFileStoreDir(dir.toString());
        AiRuntimeConfig config = new AiRuntimeConfig(properties);

        assertInstanceOf(FileChatMemoryStore.class, config.chatMemoryStore());
    }

    @Test
    void chatMemoryStoreShouldRejectUnsupportedStoreType() {
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setStoreType("redis");
        AiRuntimeConfig config = new AiRuntimeConfig(properties);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, config::chatMemoryStore);
        assertEquals("Unsupported chat-memory.store-type: redis", ex.getMessage());
    }

    @Test
    void toolResultArtifactStoreShouldUseConfiguredLimit() throws Exception {
        Path dir = Files.createTempDirectory("tool-artifact-");
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setToolResultArtifactStoreDir(dir.toString());
        properties.getChatMemory().setToolResultArtifactMaxIndexEntriesPerMemory(7);
        AiRuntimeConfig config = new AiRuntimeConfig(properties);

        ToolResultArtifactStore store = config.toolResultArtifactStore();
        assertInstanceOf(FileToolResultArtifactStore.class, store);
        String id = store.save("conv", "tool", "{}", "result");
        assertEquals("result", store.load(id).orElseThrow());
    }

    @Test
    void toolResultArtifactStoreShouldUseInMemoryStoreWhenMemoryStoreTypeIsInMemory() {
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setStoreType("in-memory");
        properties.getChatMemory().setToolResultArtifactMaxIndexEntriesPerMemory(3);
        AiRuntimeConfig config = new AiRuntimeConfig(properties);

        ToolResultArtifactStore store = config.toolResultArtifactStore();
        assertInstanceOf(InMemoryToolResultArtifactStore.class, store);
        String first = store.save("conv", "tool", "{\"q\":\"1\"}", "result-1");
        String second = store.save("conv", "tool", "{\"q\":\"2\"}", "result-2");
        store.save("conv", "tool", "{\"q\":\"3\"}", "result-3");
        store.save("conv", "tool", "{\"q\":\"4\"}", "result-4");

        assertEquals("result-1", store.load(first).orElseThrow());
        assertEquals("result-2", store.load(second).orElseThrow());
        assertEquals(3, store.recent("conv", 10).size());
    }

    @Test
    void toolResultArtifactStoreShouldRejectUnsupportedStoreType() {
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setStoreType("redis");
        AiRuntimeConfig config = new AiRuntimeConfig(properties);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, config::toolResultArtifactStore);
        assertEquals("Unsupported chat-memory.store-type: redis", ex.getMessage());
    }

    @Test
    void runtimeToolResultArtifactStoreBeanShouldAdaptInfraStore() throws Exception {
        Path dir = Files.createTempDirectory("tool-artifact-runtime-adapter-");
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setToolResultArtifactStoreDir(dir.toString());
        AiRuntimeConfig config = new AiRuntimeConfig(properties);
        ToolResultArtifactStore infraStore = config.toolResultArtifactStore();

        AiToolResultArtifactStore runtimeStore = config.aiToolResultArtifactStore(infraStore);
        String artifactId = runtimeStore.save("conv-runtime", "browser", "{\"q\":\"weather\"}", "payload");

        assertEquals("payload", runtimeStore.load(artifactId).orElseThrow());
        assertEquals(1, runtimeStore.recent("conv-runtime", 10).size());
        assertEquals(artifactId, runtimeStore.recent("conv-runtime", 10).get(0).artifactId());
    }

    @Test
    void runtimeToolResultArtifactStoreShouldReturnEmptyRecentForNonIndexStore() {
        OpenManusProperties properties = new OpenManusProperties();
        AiRuntimeConfig config = new AiRuntimeConfig(properties);
        ToolResultArtifactStore noIndexStore = new ToolResultArtifactStore() {
            @Override
            public String save(Object memoryId, String toolName, String toolArguments, String outcome) {
                return "sha256:" + "1".repeat(64);
            }

            @Override
            public java.util.Optional<String> load(String artifactId) {
                return java.util.Optional.of("payload");
            }
        };

        AiToolResultArtifactStore runtimeStore = config.aiToolResultArtifactStore(noIndexStore);
        assertTrue(runtimeStore.recent("conv", 5).isEmpty());
    }

    @Test
    void chatMemoryStoreShouldFallbackToDefaultDirWhenConfiguredDirIsBlank() {
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setStoreType("file");
        properties.getChatMemory().setFileStoreDir("   ");
        AiRuntimeConfig config = new AiRuntimeConfig(properties);

        assertInstanceOf(FileChatMemoryStore.class, config.chatMemoryStore());
    }

    @Test
    void toolResultArtifactStoreShouldFallbackToDefaultDirWhenConfiguredDirIsBlank() {
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setToolResultArtifactStoreDir("   ");
        AiRuntimeConfig config = new AiRuntimeConfig(properties);

        assertInstanceOf(FileToolResultArtifactStore.class, config.toolResultArtifactStore());
    }

    @Test
    void chatModelShouldFallbackToOpenAiWhenApiTypeBlank() {
        OpenManusProperties properties = new OpenManusProperties();
        properties.getLlm().getDefaultLlm().setApiType("  ");
        properties.getLlm().getDefaultLlm().setModel("gpt-4o-mini");
        AiRuntimeConfig config = new AiRuntimeConfig(properties);
        CapturingClient openAi = new CapturingClient();

        AiChatModel model = config.chatModel(
                new AiProviderClientRegistry(Map.of(AiProviderType.OPENAI, openAi)),
                new ObjectMapper()
        );
        model.chat(new AiChatRequest("", List.of(AiChatMessage.user("hi")), List.of(), null, null, null, null));
        assertEquals(1, openAi.callCount);
    }

    @Test
    void chatModelShouldFallbackToOpenAiWhenApiTypeNull() {
        OpenManusProperties properties = new OpenManusProperties();
        properties.getLlm().getDefaultLlm().setApiType(null);
        properties.getLlm().getDefaultLlm().setModel("gpt-4o-mini");
        AiRuntimeConfig config = new AiRuntimeConfig(properties);
        CapturingClient openAi = new CapturingClient();

        AiChatModel model = config.chatModel(
                new AiProviderClientRegistry(Map.of(AiProviderType.OPENAI, openAi)),
                new ObjectMapper()
        );
        model.chat(new AiChatRequest("", List.of(AiChatMessage.user("hi")), List.of(), null, null, null, null));
        assertEquals(1, openAi.callCount);
    }

    @Test
    void chatModelShouldFallbackToSafeDefaultWhenDefaultLlmMissing() {
        OpenManusProperties properties = new OpenManusProperties();
        properties.getLlm().setDefaultLlm(null);
        AiRuntimeConfig config = new AiRuntimeConfig(properties);
        CapturingClient openAi = new CapturingClient();

        AiChatModel model = config.chatModel(
                new AiProviderClientRegistry(Map.of(AiProviderType.OPENAI, openAi)),
                new ObjectMapper()
        );
        model.chat(new AiChatRequest("", List.of(AiChatMessage.user("hi")), List.of(), null, null, null, null));
        assertEquals(1, openAi.callCount);
    }

    @Test
    void chatModelShouldFallbackToDefaultModelWhenConfiguredModelBlank() {
        OpenManusProperties properties = new OpenManusProperties();
        properties.getLlm().getDefaultLlm().setApiType("openai");
        properties.getLlm().getDefaultLlm().setModel("   ");
        AiRuntimeConfig config = new AiRuntimeConfig(properties);
        CapturingClient openAi = new CapturingClient();

        AiChatModel model = config.chatModel(
                new AiProviderClientRegistry(Map.of(AiProviderType.OPENAI, openAi)),
                new ObjectMapper()
        );
        model.chat(new AiChatRequest("", List.of(AiChatMessage.user("hi")), List.of(), null, null, null, null));

        assertEquals("gpt-5.4", openAi.capturedRequest.getModel());
    }

    private static class CapturingClient implements AiProviderClient {
        private int callCount;
        private ChatRequestEnvelope capturedRequest;

        @Override
        public ChatResponseEnvelope chat(ChatRequestEnvelope request) {
            callCount++;
            capturedRequest = request;
            return ChatResponseEnvelope.builder()
                    .providerType(AiProviderType.OPENAI)
                    .content("ok")
                    .build();
        }

        @Override
        public void streamChat(ChatRequestEnvelope request, StreamListener listener) {
            throw new UnsupportedOperationException("not used");
        }
    }
}
