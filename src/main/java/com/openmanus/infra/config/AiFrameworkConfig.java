package com.openmanus.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.aiframework.api.AiProviderClient;
import com.openmanus.aiframework.assembler.AnthropicRequestAssembler;
import com.openmanus.aiframework.assembler.GeminiRequestAssembler;
import com.openmanus.aiframework.assembler.OpenAiRequestAssembler;
import com.openmanus.aiframework.client.AnthropicClient;
import com.openmanus.aiframework.client.GeminiClient;
import com.openmanus.aiframework.client.OpenAiClient;
import com.openmanus.aiframework.config.AiProviderClientRegistry;
import com.openmanus.aiframework.model.AiProviderType;
import com.openmanus.aiframework.model.ProviderConfig;
import com.openmanus.aiframework.parser.AnthropicResponseParser;
import com.openmanus.aiframework.parser.GeminiResponseParser;
import com.openmanus.aiframework.parser.OpenAiResponseParser;
import com.openmanus.aiframework.transport.HttpTransport;
import com.openmanus.aiframework.transport.SseTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Configuration
public class AiFrameworkConfig {

    private static final String OPENAI_FALLBACK_MODEL = "gpt-5.4";

    @Bean
    public HttpClient aiFrameworkHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Bean
    public HttpTransport aiFrameworkHttpTransport(HttpClient aiFrameworkHttpClient, ObjectMapper objectMapper) {
        return new HttpTransport(aiFrameworkHttpClient, objectMapper);
    }

    @Bean
    public SseTransport aiFrameworkSseTransport(HttpClient aiFrameworkHttpClient, ObjectMapper objectMapper) {
        return new SseTransport(aiFrameworkHttpClient, objectMapper);
    }

    @Bean
    public OpenAiClient openAiClient(OpenManusProperties properties,
                                     HttpTransport aiFrameworkHttpTransport,
                                     SseTransport aiFrameworkSseTransport,
                                     ObjectMapper objectMapper) {
        ProviderConfig config = resolveProviderConfig(properties, AiProviderType.OPENAI,
                "https://api.openai.com/v1", OPENAI_FALLBACK_MODEL);
        return new OpenAiClient(
                config,
                new OpenAiRequestAssembler(objectMapper),
                new OpenAiResponseParser(),
                aiFrameworkHttpTransport,
                aiFrameworkSseTransport,
                objectMapper
        );
    }

    @Bean
    public AnthropicClient anthropicClient(OpenManusProperties properties,
                                           HttpTransport aiFrameworkHttpTransport,
                                           SseTransport aiFrameworkSseTransport,
                                           ObjectMapper objectMapper) {
        ProviderConfig config = resolveProviderConfig(properties, AiProviderType.ANTHROPIC,
                "https://api.anthropic.com", "claude-3-5-sonnet-latest");
        return new AnthropicClient(
                config,
                new AnthropicRequestAssembler(objectMapper),
                new AnthropicResponseParser(),
                aiFrameworkHttpTransport,
                aiFrameworkSseTransport,
                objectMapper
        );
    }

    @Bean
    public GeminiClient geminiClient(OpenManusProperties properties,
                                     HttpTransport aiFrameworkHttpTransport,
                                     SseTransport aiFrameworkSseTransport,
                                     ObjectMapper objectMapper) {
        ProviderConfig config = resolveProviderConfig(properties, AiProviderType.GEMINI,
                "https://generativelanguage.googleapis.com", "gemini-1.5-pro");
        return new GeminiClient(
                config,
                new GeminiRequestAssembler(objectMapper),
                new GeminiResponseParser(),
                aiFrameworkHttpTransport,
                aiFrameworkSseTransport,
                objectMapper
        );
    }

    @Bean
    public AiProviderClientRegistry aiProviderClientRegistry(OpenAiClient openAiClient,
                                                             AnthropicClient anthropicClient,
                                                             GeminiClient geminiClient) {
        Map<AiProviderType, AiProviderClient> map = new EnumMap<>(AiProviderType.class);
        map.put(AiProviderType.OPENAI, openAiClient);
        map.put(AiProviderType.ANTHROPIC, anthropicClient);
        map.put(AiProviderType.GEMINI, geminiClient);
        return new AiProviderClientRegistry(map);
    }

    private ProviderConfig resolveProviderConfig(OpenManusProperties properties,
                                                 AiProviderType providerType,
                                                 String defaultBaseUrl,
                                                 String fallbackModel) {
        log.info("resolveProviderConfig: providerType={}, defaultBaseUrl={}, fallbackModel={}", providerType, defaultBaseUrl, fallbackModel);
        OpenManusProperties.LlmConfig llm = properties.getLlm();
        if (llm == null) {
            llm = new OpenManusProperties.LlmConfig();
        }
        OpenManusProperties.LlmConfig.DefaultLLM defaultLlm = llm.getDefaultLlm();
        if (defaultLlm == null) {
            defaultLlm = new OpenManusProperties.LlmConfig.DefaultLLM();
        }
        log.info("resolveProviderConfig: defaultLlm.apiType={}, baseUrl={}, apiKey={}, model={}",
                defaultLlm.getApiType(), defaultLlm.getBaseUrl(),
                defaultLlm.getApiKey() == null ? "null" : "***",
                defaultLlm.getModel());
        OpenManusProperties.LlmConfig.ProviderProfile profile = findProfile(llm.getProviders(), providerType);

        String baseUrl = nonBlank(profile == null ? null : profile.getBaseUrl(),
                isDefaultProvider(defaultLlm, providerType) ? defaultLlm.getBaseUrl() : null,
                defaultBaseUrl);

        String apiKey = nonBlank(profile == null ? null : profile.getApiKey(),
                isDefaultProvider(defaultLlm, providerType) ? defaultLlm.getApiKey() : null,
                "");

        String model = nonBlank(profile == null ? null : profile.getModel(),
                isDefaultProvider(defaultLlm, providerType) ? defaultLlm.getModel() : null,
                fallbackModel);

        Integer timeout = firstNonNull(profile == null ? null : profile.getTimeout(),
                isDefaultProvider(defaultLlm, providerType) ? defaultLlm.getTimeout() : null,
                120);

        Integer maxRetries = firstNonNull(profile == null ? null : profile.getMaxRetries(), 1);

        return ProviderConfig.builder()
                .providerType(providerType)
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .model(model)
                .timeoutSeconds(timeout)
                .maxRetries(maxRetries)
                .build();
    }

    private OpenManusProperties.LlmConfig.ProviderProfile findProfile(
            Map<String, OpenManusProperties.LlmConfig.ProviderProfile> providers,
            AiProviderType providerType) {
        if (providers == null || providers.isEmpty()) {
            return null;
        }
        String key = providerType.name().toLowerCase(Locale.ROOT);
        OpenManusProperties.LlmConfig.ProviderProfile profile = providers.get(key);
        if (profile != null) {
            return profile;
        }
        profile = providers.entrySet().stream()
                .filter(entry -> entry.getKey() != null && key.equalsIgnoreCase(entry.getKey().trim()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        if (profile != null) {
            return profile;
        }
        return providers.values().stream()
                .filter(p -> p != null && key.equalsIgnoreCase(trimToEmpty(p.getApiType())))
                .findFirst()
                .orElse(null);
    }

    private boolean isDefaultProvider(OpenManusProperties.LlmConfig.DefaultLLM defaultLlm, AiProviderType providerType) {
        return resolveDefaultProviderType(defaultLlm) == providerType;
    }

    private AiProviderType resolveDefaultProviderType(OpenManusProperties.LlmConfig.DefaultLLM defaultLlm) {
        String configuredType = trimToEmpty(defaultLlm.getApiType());
        if (configuredType.isEmpty()) {
            return AiProviderType.OPENAI;
        }
        try {
            return AiProviderType.from(configuredType);
        } catch (IllegalArgumentException ignored) {
            return AiProviderType.OPENAI;
        }
    }

    private String nonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
