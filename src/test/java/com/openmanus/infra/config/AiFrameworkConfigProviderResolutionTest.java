package com.openmanus.infra.config;

import com.openmanus.aiframework.model.AiProviderType;
import com.openmanus.aiframework.model.ProviderConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AiFrameworkConfigProviderResolutionTest {

    @Test
    void shouldTreatBlankDefaultApiTypeAsOpenAiWhenResolvingProviderConfig() throws Exception {
        OpenManusProperties properties = new OpenManusProperties();
        properties.getLlm().getDefaultLlm().setApiType("  ");
        properties.getLlm().getDefaultLlm().setApiKey("openai-key");
        properties.getLlm().getDefaultLlm().setBaseUrl("https://custom-openai.example/v1");
        properties.getLlm().getDefaultLlm().setModel("gpt-5-mini");
        properties.getLlm().getDefaultLlm().setTimeout(66);

        ProviderConfig config = invokeResolveProviderConfig(
                new AiFrameworkConfig(),
                properties,
                AiProviderType.OPENAI,
                "https://api.openai.com/v1",
                "fallback-model"
        );

        assertEquals(AiProviderType.OPENAI, config.getProviderType());
        assertEquals("openai-key", config.getApiKey());
        assertEquals("https://custom-openai.example/v1", config.getBaseUrl());
        assertEquals("gpt-5-mini", config.getModel());
        assertEquals(66, config.getTimeoutSeconds());
    }

    @Test
    void shouldResolveProviderProfileByCaseInsensitiveProviderKey() throws Exception {
        OpenManusProperties properties = new OpenManusProperties();
        Map<String, OpenManusProperties.LlmConfig.ProviderProfile> providers = new LinkedHashMap<>();
        OpenManusProperties.LlmConfig.ProviderProfile openAiProfile =
                new OpenManusProperties.LlmConfig.ProviderProfile();
        openAiProfile.setBaseUrl("https://upper-key-openai.example/v1");
        openAiProfile.setApiKey("upper-key");
        openAiProfile.setModel("gpt-5-nano");
        openAiProfile.setTimeout(37);
        providers.put("OPENAI", openAiProfile);
        properties.getLlm().setProviders(providers);

        ProviderConfig config = invokeResolveProviderConfig(
                new AiFrameworkConfig(),
                properties,
                AiProviderType.OPENAI,
                "https://api.openai.com/v1",
                "fallback-model"
        );

        assertEquals("https://upper-key-openai.example/v1", config.getBaseUrl());
        assertEquals("upper-key", config.getApiKey());
        assertEquals("gpt-5-nano", config.getModel());
        assertEquals(37, config.getTimeoutSeconds());
    }

    @Test
    void shouldUseOpenAiFallbackModelWhenDefaultProviderIsNotOpenAiAndProfileMissing() throws Exception {
        OpenManusProperties properties = new OpenManusProperties();
        properties.getLlm().getDefaultLlm().setApiType("anthropic");
        properties.getLlm().getDefaultLlm().setModel("claude-3-7-sonnet");

        ProviderConfig config = invokeResolveProviderConfig(
                new AiFrameworkConfig(),
                properties,
                AiProviderType.OPENAI,
                "https://api.openai.com/v1",
                "gpt-4o-mini"
        );

        assertEquals("gpt-4o-mini", config.getModel());
    }

    @Test
    void shouldResolveProviderConfigWhenDefaultLlmIsNull() throws Exception {
        OpenManusProperties properties = new OpenManusProperties();
        properties.getLlm().setDefaultLlm(null);

        ProviderConfig config = invokeResolveProviderConfig(
                new AiFrameworkConfig(),
                properties,
                AiProviderType.OPENAI,
                "https://api.openai.com/v1",
                "gpt-4o-mini"
        );

        assertNotNull(config);
        assertEquals(AiProviderType.OPENAI, config.getProviderType());
        assertEquals("https://api.openai.com/v1", config.getBaseUrl());
        assertEquals("gpt-5.4", config.getModel());
    }

    @Test
    void shouldTreatOpenAiCompatibleAsOpenAiDefaultProvider() throws Exception {
        OpenManusProperties properties = new OpenManusProperties();
        properties.getLlm().getDefaultLlm().setApiType("openai-compatible");
        properties.getLlm().getDefaultLlm().setApiKey("compatible-key");
        properties.getLlm().getDefaultLlm().setBaseUrl("https://compatible-openai.example/v1");
        properties.getLlm().getDefaultLlm().setModel("gpt-5.4");
        properties.getLlm().getDefaultLlm().setTimeout(88);

        ProviderConfig config = invokeResolveProviderConfig(
                new AiFrameworkConfig(),
                properties,
                AiProviderType.OPENAI,
                "https://api.openai.com/v1",
                "gpt-4o-mini"
        );

        assertEquals("compatible-key", config.getApiKey());
        assertEquals("https://compatible-openai.example/v1", config.getBaseUrl());
        assertEquals("gpt-5.4", config.getModel());
        assertEquals(88, config.getTimeoutSeconds());
    }

    @Test
    void shouldFallbackToOpenAiDefaultConfigWhenDefaultApiTypeUnknown() throws Exception {
        OpenManusProperties properties = new OpenManusProperties();
        properties.getLlm().getDefaultLlm().setApiType("unknown-provider");
        properties.getLlm().getDefaultLlm().setApiKey("fallback-openai-key");
        properties.getLlm().getDefaultLlm().setBaseUrl("https://fallback-openai.example/v1");
        properties.getLlm().getDefaultLlm().setModel("gpt-5.4-mini");
        properties.getLlm().getDefaultLlm().setTimeout(91);

        ProviderConfig config = invokeResolveProviderConfig(
                new AiFrameworkConfig(),
                properties,
                AiProviderType.OPENAI,
                "https://api.openai.com/v1",
                "gpt-4o-mini"
        );

        assertEquals(AiProviderType.OPENAI, config.getProviderType());
        assertEquals("fallback-openai-key", config.getApiKey());
        assertEquals("https://fallback-openai.example/v1", config.getBaseUrl());
        assertEquals("gpt-5.4-mini", config.getModel());
        assertEquals(91, config.getTimeoutSeconds());
    }

    private ProviderConfig invokeResolveProviderConfig(AiFrameworkConfig target,
                                                       OpenManusProperties properties,
                                                       AiProviderType providerType,
                                                       String defaultBaseUrl,
                                                       String fallbackModel) throws Exception {
        Method method = AiFrameworkConfig.class.getDeclaredMethod(
                "resolveProviderConfig",
                OpenManusProperties.class,
                AiProviderType.class,
                String.class,
                String.class
        );
        method.setAccessible(true);
        return (ProviderConfig) method.invoke(target, properties, providerType, defaultBaseUrl, fallbackModel);
    }
}
