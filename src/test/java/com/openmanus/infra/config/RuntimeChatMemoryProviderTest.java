package com.openmanus.infra.config;

import com.openmanus.aiframework.runtime.AiMemory;
import com.openmanus.aiframework.runtime.AiMemoryProvider;
import com.openmanus.aiframework.runtime.model.AiChatMessage;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeChatMemoryProviderTest {

    @Test
    void shouldKeepMessagesAcrossProviderGetsForSameConversationId() throws Exception {
        Path tempDir = Files.createTempDirectory("chat-memory-test-");
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setFileStoreDir(tempDir.toString());
        AiRuntimeConfig config = new AiRuntimeConfig(properties);
        AiMemoryProvider provider = config.chatMemoryProvider(config.chatMemoryStore());

        AiMemory first = provider.get("conv-a");
        first.add(AiChatMessage.user("hello"));

        AiMemory second = provider.get("conv-a");
        assertEquals(1, second.messages().stream().filter(message -> message.role() == AiChatMessage.Role.USER).count());
    }

    @Test
    void shouldPersistMessagesAcrossConfigInstances() throws Exception {
        Path tempDir = Files.createTempDirectory("chat-memory-persist-");
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setStoreType("file");
        properties.getChatMemory().setFileStoreDir(tempDir.toString());
        AiRuntimeConfig config = new AiRuntimeConfig(properties);
        AiMemoryProvider provider = config.chatMemoryProvider(config.chatMemoryStore());

        AiMemory firstRun = provider.get("conv-persist");
        firstRun.add(AiChatMessage.user("u1"));

        AiRuntimeConfig anotherConfig = new AiRuntimeConfig(properties);
        AiMemoryProvider anotherProvider = anotherConfig.chatMemoryProvider(anotherConfig.chatMemoryStore());
        AiMemory secondRun = anotherProvider.get("conv-persist");
        long userCount = secondRun.messages().stream().filter(message -> message.role() == AiChatMessage.Role.USER).count();
        assertEquals(1, userCount);
    }

    @Test
    void shouldUseInMemoryStoreTypeWhenConfigured() {
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setStoreType("in-memory");
        AiRuntimeConfig config = new AiRuntimeConfig(properties);

        AiMemoryProvider provider = config.chatMemoryProvider(config.chatMemoryStore());
        AiMemory first = provider.get("conv-mem");
        first.add(AiChatMessage.user("u1"));

        AiMemory second = provider.get("conv-mem");
        assertEquals(1, second.messages().stream().filter(message -> message.role() == AiChatMessage.Role.USER).count());
    }

    @Test
    void inMemoryStoreShouldNotPersistAcrossConfigInstances() {
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setStoreType("in-memory");

        AiRuntimeConfig firstConfig = new AiRuntimeConfig(properties);
        AiMemoryProvider firstProvider = firstConfig.chatMemoryProvider(firstConfig.chatMemoryStore());
        firstProvider.get("conv-mem-persist").add(AiChatMessage.user("u1"));

        AiRuntimeConfig secondConfig = new AiRuntimeConfig(properties);
        AiMemoryProvider secondProvider = secondConfig.chatMemoryProvider(secondConfig.chatMemoryStore());
        long userCount = secondProvider.get("conv-mem-persist").messages().stream()
                .filter(message -> message.role() == AiChatMessage.Role.USER)
                .count();
        assertEquals(0, userCount);
    }
}
