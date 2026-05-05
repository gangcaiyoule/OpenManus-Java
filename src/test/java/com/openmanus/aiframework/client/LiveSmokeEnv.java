package com.openmanus.aiframework.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

final class LiveSmokeEnv {

    private static final Set<String> OPENAI_PLACEHOLDER_API_KEYS = Set.of(
            "your-openai-live-api-key-here",
            "your-openai-compatible-api-key-here"
    );
    private static final List<String> OPENAI_FALLBACK_MODELS = List.of(
            "gpt-5.4",
            "gpt-5",
            "gpt-4o"
    );
    private static final Set<String> ANTHROPIC_PLACEHOLDER_API_KEYS = Set.of(
            "your-anthropic-live-api-key-here",
            "your-anthropic-api-key-here"
    );
    private static final Set<String> GEMINI_PLACEHOLDER_API_KEYS = Set.of(
            "your-gemini-live-api-key-here",
            "your-gemini-api-key-here"
    );

    private LiveSmokeEnv() {
    }

    static ProviderEnv openAiCompatible() {
        return openAiCompatible(System.getenv()::get);
    }

    static ProviderEnv openAiCompatible(Function<String, String> envLookup) {
        List<String> candidateModels = collectOpenAiModels(envLookup);
        return new ProviderEnv(
                candidateModels.isEmpty() ? null : candidateModels.getFirst(),
                firstNonBlankEnv(envLookup,
                        "OPENMANUS_LIVE_BASE_URL",
                        "OPENMANUS_LLM_DEFAULT_LLM_BASE_URL",
                        "OPENAI_BASE_URL"),
                firstNonBlankEnv(envLookup,
                        "OPENMANUS_LIVE_API_KEY",
                        "OPENMANUS_LLM_DEFAULT_LLM_API_KEY",
                        "OPENAI_API_KEY"),
                candidateModels,
                OPENAI_PLACEHOLDER_API_KEYS
        );
    }

    static ProviderEnv anthropic() {
        return anthropic(System.getenv()::get);
    }

    static ProviderEnv anthropic(Function<String, String> envLookup) {
        return new ProviderEnv(
                firstNonBlankEnv(envLookup,
                        "OPENMANUS_LIVE_ANTHROPIC_MODEL",
                        "OPENMANUS_LLM_PROVIDERS_ANTHROPIC_MODEL"),
                firstNonBlankEnv(envLookup,
                        "OPENMANUS_LIVE_ANTHROPIC_BASE_URL",
                        "OPENMANUS_LLM_PROVIDERS_ANTHROPIC_BASE_URL"),
                firstNonBlankEnv(envLookup,
                        "OPENMANUS_LIVE_ANTHROPIC_API_KEY",
                        "OPENMANUS_LLM_PROVIDERS_ANTHROPIC_API_KEY"),
                singleModelCandidate(firstNonBlankEnv(envLookup,
                        "OPENMANUS_LIVE_ANTHROPIC_MODEL",
                        "OPENMANUS_LLM_PROVIDERS_ANTHROPIC_MODEL")),
                ANTHROPIC_PLACEHOLDER_API_KEYS
        );
    }

    static ProviderEnv gemini() {
        return gemini(System.getenv()::get);
    }

    static ProviderEnv gemini(Function<String, String> envLookup) {
        return new ProviderEnv(
                firstNonBlankEnv(envLookup,
                        "OPENMANUS_LIVE_GEMINI_MODEL",
                        "OPENMANUS_LLM_PROVIDERS_GEMINI_MODEL"),
                firstNonBlankEnv(envLookup,
                        "OPENMANUS_LIVE_GEMINI_BASE_URL",
                        "OPENMANUS_LLM_PROVIDERS_GEMINI_BASE_URL"),
                firstNonBlankEnv(envLookup,
                        "OPENMANUS_LIVE_GEMINI_API_KEY",
                        "OPENMANUS_LLM_PROVIDERS_GEMINI_API_KEY"),
                singleModelCandidate(firstNonBlankEnv(envLookup,
                        "OPENMANUS_LIVE_GEMINI_MODEL",
                        "OPENMANUS_LLM_PROVIDERS_GEMINI_MODEL")),
                GEMINI_PLACEHOLDER_API_KEYS
        );
    }

    private static List<String> collectOpenAiModels(Function<String, String> envLookup) {
        List<String> liveModels = collectScopedModels(envLookup,
                "OPENMANUS_LIVE_MODEL",
                "OPENMANUS_LIVE_MODEL_CANDIDATES");
        if (!liveModels.isEmpty()) {
            return liveModels;
        }

        List<String> defaultModels = collectScopedModels(envLookup,
                "OPENMANUS_LLM_DEFAULT_LLM_MODEL",
                "OPENMANUS_LLM_DEFAULT_LLM_MODEL_CANDIDATES");
        if (!defaultModels.isEmpty()) {
            return defaultModels;
        }

        List<String> legacyModels = collectScopedModels(envLookup,
                "OPENAI_MODEL",
                "OPENAI_MODEL_CANDIDATES");
        if (!legacyModels.isEmpty()) {
            return legacyModels;
        }

        return OPENAI_FALLBACK_MODELS;
    }

    private static List<String> collectScopedModels(Function<String, String> envLookup,
                                                    String modelKey,
                                                    String candidatesKey) {
        LinkedHashSet<String> models = new LinkedHashSet<>();
        addNonBlank(models, envLookup.apply(modelKey));
        addCsvValues(models, envLookup.apply(candidatesKey));
        return List.copyOf(models);
    }

    private static String firstNonBlankEnv(Function<String, String> envLookup, String... keys) {
        for (String key : keys) {
            String value = envLookup.apply(key);
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return null;
    }

    private static void addCsvValues(LinkedHashSet<String> target, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return;
        }
        for (String part : rawValue.split(",")) {
            addNonBlank(target, part);
        }
    }

    private static void addNonBlank(LinkedHashSet<String> target, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (!trimmed.isEmpty()) {
            target.add(trimmed);
        }
    }

    private static List<String> singleModelCandidate(String model) {
        if (model == null || model.isBlank()) {
            return List.of();
        }
        return List.of(model.trim());
    }

    record ProviderEnv(String model,
                       String baseUrl,
                       String apiKey,
                       List<String> candidateModels,
                       Set<String> placeholderApiKeys) {
        boolean isConfigured() {
            return !candidateModels().isEmpty() && notBlank(baseUrl) && hasRealApiKey();
        }

        private boolean notBlank(String value) {
            return value != null && !value.trim().isEmpty();
        }

        public List<String> candidateModels() {
            if (candidateModels == null || candidateModels.isEmpty()) {
                if (!notBlank(model)) {
                    return List.of();
                }
                return List.of(model.trim());
            }
            List<String> result = new ArrayList<>(candidateModels.size());
            for (String candidate : candidateModels) {
                if (notBlank(candidate)) {
                    result.add(candidate.trim());
                }
            }
            return List.copyOf(result);
        }

        private boolean hasRealApiKey() {
            if (!notBlank(apiKey)) {
                return false;
            }
            return !placeholderApiKeys.contains(apiKey.trim());
        }
    }
}
