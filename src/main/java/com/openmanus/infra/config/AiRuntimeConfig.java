package com.openmanus.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.aiframework.config.AiProviderClientRegistry;
import com.openmanus.aiframework.model.AiProviderType;
import com.openmanus.aiframework.runtime.AiChatModel;
import com.openmanus.aiframework.runtime.AiMemoryProvider;
import com.openmanus.aiframework.runtime.AiMemoryStore;
import com.openmanus.aiframework.runtime.AiProviderChatModel;
import com.openmanus.infra.memory.FileChatMemoryStore;
import com.openmanus.infra.memory.InMemoryAiMemoryStore;
import com.openmanus.infra.memory.PersistentAiMemory;
import java.nio.file.Path;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Runtime config for AI model and memory wiring.
 */
@Configuration
public class AiRuntimeConfig {

  private final OpenManusProperties properties;

  public AiRuntimeConfig(OpenManusProperties properties) {
    this.properties = properties;
  }

  /**
   * Runtime memory provider.
   */
  @Bean
  public AiMemoryProvider chatMemoryProvider(AiMemoryStore chatMemoryStore) {
    return conversationId -> new PersistentAiMemory(conversationId, chatMemoryStore);
  }

  /**
   * Runtime memory store.
   */
  @Bean
  public AiMemoryStore chatMemoryStore() {
    OpenManusProperties.ChatMemoryConfig cfg = properties.getChatMemory();
    String storeType = (cfg.getStoreType() == null || cfg.getStoreType().trim().isEmpty())
        ? "file"
        : cfg.getStoreType().trim().toLowerCase(Locale.ROOT);
    if ("in-memory".equals(storeType) || "inmemory".equals(storeType)) {
      return new InMemoryAiMemoryStore();
    }
    if (!"file".equals(storeType)) {
      throw new IllegalArgumentException(
          "Unsupported chat-memory.store-type: " + cfg.getStoreType());
    }
    Path dir = resolveDirectory(
        cfg.getFileStoreDir(),
        OpenManusProperties.ChatMemoryConfig.DEFAULT_FILE_STORE_DIR
    );
    return new FileChatMemoryStore(dir, cfg.getRetentionDays(), cfg.isQuarantineCorruptedFiles());
  }

  /**
   * Runtime chat model.
   */
  @Bean
  public AiChatModel chatModel(
      AiProviderClientRegistry aiProviderClientRegistry, ObjectMapper objectMapper) {
    OpenManusProperties.LlmConfig llmConfigRoot = properties.getLlm();
    OpenManusProperties.LlmConfig.DefaultLLM llmConfig =
        llmConfigRoot == null || llmConfigRoot.getDefaultLlm() == null
            ? new OpenManusProperties.LlmConfig.DefaultLLM()
            : llmConfigRoot.getDefaultLlm();
    AiProviderType providerType = resolveDefaultProviderType(llmConfig.getApiType());
    String defaultModel = resolveDefaultModel(llmConfig.getModel());
    return new AiProviderChatModel(
        aiProviderClientRegistry,
        objectMapper,
        defaultModel,
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

  private Path resolveDirectory(String configured, String fallback) {
    String value = configured == null ? "" : configured.trim();
    if (value.isEmpty()) {
      value = fallback;
    }
    return Path.of(value);
  }

  private String resolveDefaultModel(String configuredModel) {
    if (configuredModel != null && !configuredModel.isBlank()) {
      return configuredModel.trim();
    }
    String fallback = new OpenManusProperties.LlmConfig.DefaultLLM().getModel();
    return fallback == null ? "" : fallback.trim();
  }
}
