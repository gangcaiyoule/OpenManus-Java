package com.openmanus.aiframework.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveSmokeEnvTest {

    @Test
    void shouldPreferExplicitOpenAiLiveEnvValues() {
        LiveSmokeEnv.ProviderEnv env = LiveSmokeEnv.openAiCompatible(key -> switch (key) {
            case "OPENMANUS_LIVE_MODEL" -> "live-model";
            case "OPENMANUS_LIVE_BASE_URL" -> "https://live.example/v1";
            case "OPENMANUS_LIVE_API_KEY" -> "live-key";
            case "OPENMANUS_LLM_DEFAULT_LLM_MODEL" -> "default-model";
            case "OPENMANUS_LLM_DEFAULT_LLM_BASE_URL" -> "https://default.example/v1";
            case "OPENMANUS_LLM_DEFAULT_LLM_API_KEY" -> "default-key";
            default -> null;
        });

        assertEquals("live-model", env.model());
        assertEquals("live-model", env.candidateModels().getFirst());
        assertEquals("https://live.example/v1", env.baseUrl());
        assertEquals("live-key", env.apiKey());
        assertTrue(env.isConfigured());
    }

    @Test
    void shouldTrimExplicitOpenAiLiveEnvValues() {
        LiveSmokeEnv.ProviderEnv env = LiveSmokeEnv.openAiCompatible(key -> switch (key) {
            case "OPENMANUS_LIVE_MODEL" -> "  live-model  ";
            case "OPENMANUS_LIVE_BASE_URL" -> "  https://live.example/v1  ";
            case "OPENMANUS_LIVE_API_KEY" -> "  live-key  ";
            default -> null;
        });

        assertEquals("live-model", env.model());
        assertEquals("live-model", env.candidateModels().getFirst());
        assertEquals("https://live.example/v1", env.baseUrl());
        assertEquals("live-key", env.apiKey());
        assertTrue(env.isConfigured());
    }

    @Test
    void shouldFallbackToDefaultOpenAiEnvWhenLiveEnvIsMissing() {
        LiveSmokeEnv.ProviderEnv env = LiveSmokeEnv.openAiCompatible(key -> switch (key) {
            case "OPENMANUS_LLM_DEFAULT_LLM_MODEL" -> "default-model";
            case "OPENMANUS_LLM_DEFAULT_LLM_BASE_URL" -> "https://default.example/v1";
            case "OPENMANUS_LLM_DEFAULT_LLM_API_KEY" -> "default-key";
            default -> null;
        });

        assertEquals("default-model", env.model());
        assertEquals("default-model", env.candidateModels().getFirst());
        assertEquals("https://default.example/v1", env.baseUrl());
        assertEquals("default-key", env.apiKey());
        assertTrue(env.isConfigured());
    }

    @Test
    void shouldFallbackToLegacyOpenAiEnvWhenLiveAndDefaultEnvAreMissing() {
        LiveSmokeEnv.ProviderEnv env = LiveSmokeEnv.openAiCompatible(key -> switch (key) {
            case "OPENAI_MODEL" -> "legacy-model";
            case "OPENAI_BASE_URL" -> "https://legacy.example/v1";
            case "OPENAI_API_KEY" -> "legacy-key";
            default -> null;
        });

        assertEquals("legacy-model", env.model());
        assertEquals("legacy-model", env.candidateModels().getFirst());
        assertEquals("https://legacy.example/v1", env.baseUrl());
        assertEquals("legacy-key", env.apiKey());
        assertTrue(env.isConfigured());
    }

    @Test
    void shouldPreferDefaultOpenAiEnvOverLegacyOpenAiEnvFallback() {
        LiveSmokeEnv.ProviderEnv env = LiveSmokeEnv.openAiCompatible(key -> switch (key) {
            case "OPENMANUS_LLM_DEFAULT_LLM_MODEL" -> "default-model";
            case "OPENMANUS_LLM_DEFAULT_LLM_BASE_URL" -> "https://default.example/v1";
            case "OPENMANUS_LLM_DEFAULT_LLM_API_KEY" -> "default-key";
            case "OPENAI_MODEL" -> "legacy-model";
            case "OPENAI_BASE_URL" -> "https://legacy.example/v1";
            case "OPENAI_API_KEY" -> "legacy-key";
            default -> null;
        });

        assertEquals("default-model", env.model());
        assertEquals("default-model", env.candidateModels().getFirst());
        assertEquals("https://default.example/v1", env.baseUrl());
        assertEquals("default-key", env.apiKey());
        assertTrue(env.isConfigured());
    }

    @Test
    void shouldMergeAndDeduplicateOpenAiCandidateModelsWithinLiveScope() {
        LiveSmokeEnv.ProviderEnv env = LiveSmokeEnv.openAiCompatible(key -> switch (key) {
            case "OPENMANUS_LIVE_MODEL" -> "live-model";
            case "OPENMANUS_LIVE_MODEL_CANDIDATES" -> "gpt-5-mini, live-model, gpt-4o-mini";
            case "OPENMANUS_LIVE_BASE_URL" -> "https://live.example/v1";
            case "OPENMANUS_LIVE_API_KEY" -> "live-key";
            default -> null;
        });

        assertEquals(java.util.List.of("live-model", "gpt-5-mini", "gpt-4o-mini"), env.candidateModels());
        assertTrue(env.isConfigured());
    }

    @Test
    void shouldPreferLiveScopeCandidatesOverDefaultAndLegacyFallbacks() {
        LiveSmokeEnv.ProviderEnv env = LiveSmokeEnv.openAiCompatible(key -> switch (key) {
            case "OPENMANUS_LIVE_MODEL" -> "live-model";
            case "OPENMANUS_LIVE_MODEL_CANDIDATES" -> "gpt-5-mini, gpt-4o-mini";
            case "OPENMANUS_LLM_DEFAULT_LLM_MODEL" -> "default-model";
            case "OPENMANUS_LLM_DEFAULT_LLM_MODEL_CANDIDATES" -> "default-candidate";
            case "OPENAI_MODEL" -> "legacy-model";
            case "OPENAI_MODEL_CANDIDATES" -> "legacy-candidate";
            case "OPENMANUS_LIVE_BASE_URL" -> "https://live.example/v1";
            case "OPENMANUS_LIVE_API_KEY" -> "live-key";
            case "OPENMANUS_LLM_DEFAULT_LLM_BASE_URL" -> "https://default.example/v1";
            case "OPENMANUS_LLM_DEFAULT_LLM_API_KEY" -> "default-key";
            case "OPENAI_BASE_URL" -> "https://legacy.example/v1";
            case "OPENAI_API_KEY" -> "legacy-key";
            default -> null;
        });

        assertEquals("live-model", env.model());
        assertEquals(java.util.List.of("live-model", "gpt-5-mini", "gpt-4o-mini"), env.candidateModels());
        assertEquals("https://live.example/v1", env.baseUrl());
        assertEquals("live-key", env.apiKey());
        assertTrue(env.isConfigured());
    }

    @Test
    void shouldUseDefaultScopeCandidatesWhenLiveScopeHasNoModels() {
        LiveSmokeEnv.ProviderEnv env = LiveSmokeEnv.openAiCompatible(key -> switch (key) {
            case "OPENMANUS_LLM_DEFAULT_LLM_MODEL" -> "default-model";
            case "OPENMANUS_LLM_DEFAULT_LLM_MODEL_CANDIDATES" -> "gpt-5-mini, default-model, gpt-4o-mini";
            case "OPENAI_MODEL" -> "legacy-model";
            case "OPENAI_MODEL_CANDIDATES" -> "legacy-candidate";
            case "OPENMANUS_LLM_DEFAULT_LLM_BASE_URL" -> "https://default.example/v1";
            case "OPENMANUS_LLM_DEFAULT_LLM_API_KEY" -> "default-key";
            default -> null;
        });

        assertEquals("default-model", env.model());
        assertEquals(java.util.List.of("default-model", "gpt-5-mini", "gpt-4o-mini"), env.candidateModels());
        assertEquals("https://default.example/v1", env.baseUrl());
        assertEquals("default-key", env.apiKey());
        assertTrue(env.isConfigured());
    }

    @Test
    void shouldFallbackToBuiltInOpenAiCandidateModelsWhenNoExplicitModelExists() {
        LiveSmokeEnv.ProviderEnv env = LiveSmokeEnv.openAiCompatible(key -> switch (key) {
            case "OPENMANUS_LIVE_BASE_URL" -> "https://live.example/v1";
            case "OPENMANUS_LIVE_API_KEY" -> "live-key";
            default -> null;
        });

        assertEquals("gpt-5.4", env.model());
        assertEquals(java.util.List.of("gpt-5.4", "gpt-5", "gpt-4o"), env.candidateModels());
        assertTrue(env.isConfigured());
    }

    @Test
    void shouldTreatBlankValuesAsMissing() {
        assertFalse(LiveSmokeEnv.openAiCompatible(key -> switch (key) {
            case "OPENMANUS_LIVE_MODEL" -> " ";
            case "OPENMANUS_LIVE_MODEL_CANDIDATES" -> " ,  ";
            case "OPENMANUS_LIVE_BASE_URL" -> "";
            case "OPENMANUS_LIVE_API_KEY" -> "   ";
            case "OPENMANUS_LLM_DEFAULT_LLM_MODEL" -> " ";
            case "OPENMANUS_LLM_DEFAULT_LLM_MODEL_CANDIDATES" -> ",";
            case "OPENMANUS_LLM_DEFAULT_LLM_BASE_URL" -> "";
            case "OPENMANUS_LLM_DEFAULT_LLM_API_KEY" -> "   ";
            default -> null;
        }).isConfigured());
    }

    @Test
    void shouldTreatOpenAiPlaceholderApiKeysAsMissing() {
        assertFalse(LiveSmokeEnv.openAiCompatible(key -> switch (key) {
            case "OPENMANUS_LIVE_MODEL" -> "live-model";
            case "OPENMANUS_LIVE_BASE_URL" -> "https://live.example/v1";
            case "OPENMANUS_LIVE_API_KEY" -> "your-openai-live-api-key-here";
            default -> null;
        }).isConfigured());

        assertFalse(LiveSmokeEnv.openAiCompatible(key -> switch (key) {
            case "OPENMANUS_LLM_DEFAULT_LLM_MODEL" -> "default-model";
            case "OPENMANUS_LLM_DEFAULT_LLM_BASE_URL" -> "https://default.example/v1";
            case "OPENMANUS_LLM_DEFAULT_LLM_API_KEY" -> "your-openai-compatible-api-key-here";
            default -> null;
        }).isConfigured());
    }

    @Test
    void shouldFallbackAnthropicToProviderProfileEnvWhenLiveEnvIsMissing() {
        LiveSmokeEnv.ProviderEnv env = LiveSmokeEnv.anthropic(key -> switch (key) {
            case "OPENMANUS_LLM_PROVIDERS_ANTHROPIC_MODEL" -> "claude-sonnet";
            case "OPENMANUS_LLM_PROVIDERS_ANTHROPIC_BASE_URL" -> "https://anthropic.example";
            case "OPENMANUS_LLM_PROVIDERS_ANTHROPIC_API_KEY" -> "anthropic-key";
            default -> null;
        });

        assertEquals("claude-sonnet", env.model());
        assertEquals(java.util.List.of("claude-sonnet"), env.candidateModels());
        assertEquals("https://anthropic.example", env.baseUrl());
        assertEquals("anthropic-key", env.apiKey());
        assertTrue(env.isConfigured());
    }

    @Test
    void shouldPreferExplicitAnthropicLiveEnvOverProviderProfileFallback() {
        LiveSmokeEnv.ProviderEnv env = LiveSmokeEnv.anthropic(key -> switch (key) {
            case "OPENMANUS_LIVE_ANTHROPIC_MODEL" -> "live-claude";
            case "OPENMANUS_LIVE_ANTHROPIC_BASE_URL" -> "https://live-anthropic.example";
            case "OPENMANUS_LIVE_ANTHROPIC_API_KEY" -> "live-anthropic-key";
            case "OPENMANUS_LLM_PROVIDERS_ANTHROPIC_MODEL" -> "profile-claude";
            case "OPENMANUS_LLM_PROVIDERS_ANTHROPIC_BASE_URL" -> "https://profile-anthropic.example";
            case "OPENMANUS_LLM_PROVIDERS_ANTHROPIC_API_KEY" -> "profile-anthropic-key";
            default -> null;
        });

        assertEquals("live-claude", env.model());
        assertEquals(java.util.List.of("live-claude"), env.candidateModels());
        assertEquals("https://live-anthropic.example", env.baseUrl());
        assertEquals("live-anthropic-key", env.apiKey());
        assertTrue(env.isConfigured());
    }

    @Test
    void shouldFallbackAnthropicToProviderProfileWhenExplicitLiveEnvIsBlank() {
        LiveSmokeEnv.ProviderEnv env = LiveSmokeEnv.anthropic(key -> switch (key) {
            case "OPENMANUS_LIVE_ANTHROPIC_MODEL" -> " ";
            case "OPENMANUS_LIVE_ANTHROPIC_BASE_URL" -> "";
            case "OPENMANUS_LIVE_ANTHROPIC_API_KEY" -> "   ";
            case "OPENMANUS_LLM_PROVIDERS_ANTHROPIC_MODEL" -> "profile-claude";
            case "OPENMANUS_LLM_PROVIDERS_ANTHROPIC_BASE_URL" -> "https://profile-anthropic.example";
            case "OPENMANUS_LLM_PROVIDERS_ANTHROPIC_API_KEY" -> "profile-anthropic-key";
            default -> null;
        });

        assertEquals("profile-claude", env.model());
        assertEquals(java.util.List.of("profile-claude"), env.candidateModels());
        assertEquals("https://profile-anthropic.example", env.baseUrl());
        assertEquals("profile-anthropic-key", env.apiKey());
        assertTrue(env.isConfigured());
    }

    @Test
    void shouldTrimAnthropicProviderProfileFallbackValues() {
        LiveSmokeEnv.ProviderEnv env = LiveSmokeEnv.anthropic(key -> switch (key) {
            case "OPENMANUS_LLM_PROVIDERS_ANTHROPIC_MODEL" -> "  profile-claude  ";
            case "OPENMANUS_LLM_PROVIDERS_ANTHROPIC_BASE_URL" -> "  https://profile-anthropic.example  ";
            case "OPENMANUS_LLM_PROVIDERS_ANTHROPIC_API_KEY" -> "  profile-anthropic-key  ";
            default -> null;
        });

        assertEquals("profile-claude", env.model());
        assertEquals(java.util.List.of("profile-claude"), env.candidateModels());
        assertEquals("https://profile-anthropic.example", env.baseUrl());
        assertEquals("profile-anthropic-key", env.apiKey());
        assertTrue(env.isConfigured());
    }

    @Test
    void shouldTreatAnthropicPlaceholderApiKeysAsMissing() {
        assertFalse(LiveSmokeEnv.anthropic(key -> switch (key) {
            case "OPENMANUS_LIVE_ANTHROPIC_MODEL" -> "live-claude";
            case "OPENMANUS_LIVE_ANTHROPIC_BASE_URL" -> "https://live-anthropic.example";
            case "OPENMANUS_LIVE_ANTHROPIC_API_KEY" -> "your-anthropic-live-api-key-here";
            default -> null;
        }).isConfigured());

        assertFalse(LiveSmokeEnv.anthropic(key -> switch (key) {
            case "OPENMANUS_LLM_PROVIDERS_ANTHROPIC_MODEL" -> "profile-claude";
            case "OPENMANUS_LLM_PROVIDERS_ANTHROPIC_BASE_URL" -> "https://profile-anthropic.example";
            case "OPENMANUS_LLM_PROVIDERS_ANTHROPIC_API_KEY" -> "your-anthropic-api-key-here";
            default -> null;
        }).isConfigured());
    }

    @Test
    void shouldFallbackGeminiToProviderProfileEnvWhenLiveEnvIsMissing() {
        LiveSmokeEnv.ProviderEnv env = LiveSmokeEnv.gemini(key -> switch (key) {
            case "OPENMANUS_LLM_PROVIDERS_GEMINI_MODEL" -> "gemini-pro";
            case "OPENMANUS_LLM_PROVIDERS_GEMINI_BASE_URL" -> "https://gemini.example";
            case "OPENMANUS_LLM_PROVIDERS_GEMINI_API_KEY" -> "gemini-key";
            default -> null;
        });

        assertEquals("gemini-pro", env.model());
        assertEquals(java.util.List.of("gemini-pro"), env.candidateModels());
        assertEquals("https://gemini.example", env.baseUrl());
        assertEquals("gemini-key", env.apiKey());
        assertTrue(env.isConfigured());
    }

    @Test
    void shouldFallbackGeminiToProviderProfileWhenExplicitLiveEnvIsBlank() {
        LiveSmokeEnv.ProviderEnv env = LiveSmokeEnv.gemini(key -> switch (key) {
            case "OPENMANUS_LIVE_GEMINI_MODEL" -> " ";
            case "OPENMANUS_LIVE_GEMINI_BASE_URL" -> "";
            case "OPENMANUS_LIVE_GEMINI_API_KEY" -> "   ";
            case "OPENMANUS_LLM_PROVIDERS_GEMINI_MODEL" -> "profile-gemini";
            case "OPENMANUS_LLM_PROVIDERS_GEMINI_BASE_URL" -> "https://profile-gemini.example";
            case "OPENMANUS_LLM_PROVIDERS_GEMINI_API_KEY" -> "profile-gemini-key";
            default -> null;
        });

        assertEquals("profile-gemini", env.model());
        assertEquals(java.util.List.of("profile-gemini"), env.candidateModels());
        assertEquals("https://profile-gemini.example", env.baseUrl());
        assertEquals("profile-gemini-key", env.apiKey());
        assertTrue(env.isConfigured());
    }

    @Test
    void shouldTrimGeminiProviderProfileFallbackValues() {
        LiveSmokeEnv.ProviderEnv env = LiveSmokeEnv.gemini(key -> switch (key) {
            case "OPENMANUS_LLM_PROVIDERS_GEMINI_MODEL" -> "  profile-gemini  ";
            case "OPENMANUS_LLM_PROVIDERS_GEMINI_BASE_URL" -> "  https://profile-gemini.example  ";
            case "OPENMANUS_LLM_PROVIDERS_GEMINI_API_KEY" -> "  profile-gemini-key  ";
            default -> null;
        });

        assertEquals("profile-gemini", env.model());
        assertEquals("https://profile-gemini.example", env.baseUrl());
        assertEquals("profile-gemini-key", env.apiKey());
        assertTrue(env.isConfigured());
    }

    @Test
    void shouldTreatGeminiPlaceholderApiKeysAsMissing() {
        assertFalse(LiveSmokeEnv.gemini(key -> switch (key) {
            case "OPENMANUS_LIVE_GEMINI_MODEL" -> "live-gemini";
            case "OPENMANUS_LIVE_GEMINI_BASE_URL" -> "https://live-gemini.example";
            case "OPENMANUS_LIVE_GEMINI_API_KEY" -> "your-gemini-live-api-key-here";
            default -> null;
        }).isConfigured());

        assertFalse(LiveSmokeEnv.gemini(key -> switch (key) {
            case "OPENMANUS_LLM_PROVIDERS_GEMINI_MODEL" -> "profile-gemini";
            case "OPENMANUS_LLM_PROVIDERS_GEMINI_BASE_URL" -> "https://profile-gemini.example";
            case "OPENMANUS_LLM_PROVIDERS_GEMINI_API_KEY" -> "your-gemini-api-key-here";
            default -> null;
        }).isConfigured());
    }
}
