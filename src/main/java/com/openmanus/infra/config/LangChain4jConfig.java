package com.openmanus.infra.config;

import com.openmanus.infra.memory.FileChatMemoryStore;
import com.openmanus.infra.memory.FileToolResultArtifactStore;
import com.openmanus.infra.memory.PersistentChatMemory;
import com.openmanus.infra.memory.ToolResultArtifactStore;
import com.openmanus.aiframework.bridge.OpenAiFrameworkChatModel;
import com.openmanus.aiframework.config.AiProviderClientRegistry;
import com.openmanus.aiframework.model.AiProviderType;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * LangChain4j配置类
 * 
 * 配置LLM、嵌入模型、对话记忆和Agent执行器
 * 采用构建者模式创建各组件实例
 */
@Configuration
public class LangChain4jConfig {
    
    private final OpenManusProperties properties;

    public LangChain4jConfig(OpenManusProperties properties) {
        this.properties = properties;
    }

    /**
     * 对话记忆提供者
     * 使用持久化存储保留完整消息历史。
     */
    @Bean
    public ChatMemoryProvider chatMemoryProvider(ChatMemoryStore chatMemoryStore) {
        return conversationId -> new PersistentChatMemory(conversationId, chatMemoryStore);
    }

    @Bean
    public ChatMemoryStore chatMemoryStore() {
        OpenManusProperties.ChatMemoryConfig cfg = properties.getChatMemory();
        String storeType = (cfg.getStoreType() == null || cfg.getStoreType().trim().isEmpty())
                ? "file"
                : cfg.getStoreType().trim().toLowerCase();
        if ("in-memory".equals(storeType) || "inmemory".equals(storeType)) {
            return new InMemoryChatMemoryStore();
        }
        if (!"file".equals(storeType)) {
            throw new IllegalArgumentException("Unsupported chat-memory.store-type: " + cfg.getStoreType());
        }
        Path dir = Path.of(cfg.getFileStoreDir());
        return new FileChatMemoryStore(dir, cfg.getRetentionDays(), cfg.isQuarantineCorruptedFiles());
    }

    @Bean
    public ToolResultArtifactStore toolResultArtifactStore() {
        OpenManusProperties.ChatMemoryConfig cfg = properties.getChatMemory();
        Path dir = Path.of(cfg.getToolResultArtifactStoreDir());
        return new FileToolResultArtifactStore(dir, cfg.getToolResultArtifactMaxIndexEntriesPerMemory());
    }

    /**
     * 聊天模型
     * 基于配置文件创建LLM实例
     */
    @Bean
    public ChatModel chatModel(AiProviderClientRegistry aiProviderClientRegistry, ObjectMapper objectMapper) {
        OpenManusProperties.LlmConfig.DefaultLLM llmConfig = properties.getLlm().getDefaultLlm();
        AiProviderType providerType = resolveDefaultProviderType(llmConfig.getApiType());
        return new OpenAiFrameworkChatModel(
                aiProviderClientRegistry,
                objectMapper,
                llmConfig.getModel(),
                llmConfig.getTemperature(),
                llmConfig.getMaxTokens(),
                llmConfig.getTimeout(),
                providerType
        );
    }

    private AiProviderType resolveDefaultProviderType(String apiType) {
        if (apiType == null || apiType.isBlank()) {
            return AiProviderType.OPENAI;
        }
        try {
            return AiProviderType.from(apiType);
        } catch (IllegalArgumentException ignored) {
            return AiProviderType.OPENAI;
        }
    }

} 
