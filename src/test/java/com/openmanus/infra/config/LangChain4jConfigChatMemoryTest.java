package com.openmanus.infra.config;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LangChain4jConfigChatMemoryTest {

    @Test
    void shouldKeepMessagesAcrossProviderGetsForSameConversationId() throws Exception {
        Path tempDir = Files.createTempDirectory("chat-memory-test-");
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setFileStoreDir(tempDir.toString());
        LangChain4jConfig config = new LangChain4jConfig(properties);
        ChatMemoryProvider provider = config.chatMemoryProvider(config.chatMemoryStore());

        ChatMemory first = provider.get("conv-a");
        first.add(UserMessage.from("hello"));

        ChatMemory second = provider.get("conv-a");
        assertEquals(1, second.messages().stream().filter(message -> message instanceof UserMessage).count());
    }

    @Test
    void shouldPersistMessagesAcrossConfigInstances() throws Exception {
        Path tempDir = Files.createTempDirectory("chat-memory-persist-");
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setStoreType("file");
        properties.getChatMemory().setFileStoreDir(tempDir.toString());
        LangChain4jConfig config = new LangChain4jConfig(properties);
        ChatMemoryProvider provider = config.chatMemoryProvider(config.chatMemoryStore());

        ChatMemory firstRun = provider.get("conv-persist");
        firstRun.add(UserMessage.from("u1"));

        LangChain4jConfig anotherConfig = new LangChain4jConfig(properties);
        ChatMemoryProvider anotherProvider = anotherConfig.chatMemoryProvider(anotherConfig.chatMemoryStore());
        ChatMemory secondRun = anotherProvider.get("conv-persist");
        long userCount = secondRun.messages().stream().filter(message -> message instanceof UserMessage).count();
        assertEquals(1, userCount);
    }

    @Test
    void shouldUseInMemoryStoreTypeWhenConfigured() {
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setStoreType("in-memory");
        LangChain4jConfig config = new LangChain4jConfig(properties);

        ChatMemoryProvider provider = config.chatMemoryProvider(config.chatMemoryStore());
        ChatMemory first = provider.get("conv-mem");
        first.add(UserMessage.from("u1"));

        ChatMemory second = provider.get("conv-mem");
        assertEquals(1, second.messages().stream().filter(message -> message instanceof UserMessage).count());
    }

    @Test
    void inMemoryStoreShouldNotPersistAcrossConfigInstances() {
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setStoreType("in-memory");

        LangChain4jConfig firstConfig = new LangChain4jConfig(properties);
        ChatMemoryProvider firstProvider = firstConfig.chatMemoryProvider(firstConfig.chatMemoryStore());
        firstProvider.get("conv-mem-persist").add(UserMessage.from("u1"));

        LangChain4jConfig secondConfig = new LangChain4jConfig(properties);
        ChatMemoryProvider secondProvider = secondConfig.chatMemoryProvider(secondConfig.chatMemoryStore());
        long userCount = secondProvider.get("conv-mem-persist").messages().stream()
                .filter(message -> message instanceof UserMessage)
                .count();
        assertEquals(0, userCount);
    }
}
