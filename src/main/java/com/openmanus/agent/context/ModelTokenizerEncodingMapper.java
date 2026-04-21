package com.openmanus.agent.context;

import com.knuddels.jtokkit.api.EncodingType;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;

/**
 * Maps configured model names to tokenizer encodings.
 * Unknown models keep compatibility by falling back to CL100K.
 */
@Slf4j
final class ModelTokenizerEncodingMapper {

    private ModelTokenizerEncodingMapper() {
    }

    static EncodingType resolve(String modelName) {
        EncodingType resolvedEncoding;
        if (modelName == null || modelName.isBlank()) {
            resolvedEncoding = EncodingType.CL100K_BASE;
            log.debug("model-context tokenizer encoding resolved: model={}, normalized={}, encoding={}",
                    safeModel(modelName), "blank", resolvedEncoding);
            return resolvedEncoding;
        }
        String normalized = normalize(modelName);

        if (matchesAnyPrefix(normalized, "gpt-5", "gpt-4.1", "gpt-4o", "o1", "o3", "o4")
                || containsAny(normalized, "claude", "gemini", "deepseek", "llama", "glm", "kimi")) {
            resolvedEncoding = EncodingType.O200K_BASE;
            log.debug("model-context tokenizer encoding resolved: model={}, normalized={}, encoding={}",
                    safeModel(modelName), normalized, resolvedEncoding);
            return resolvedEncoding;
        }

        if (containsAny(normalized, "davinci", "curie", "babbage", "ada", "codex")) {
            if (containsAny(normalized, "code", "edit")) {
                resolvedEncoding = EncodingType.P50K_BASE;
                log.debug("model-context tokenizer encoding resolved: model={}, normalized={}, encoding={}",
                        safeModel(modelName), normalized, resolvedEncoding);
                return resolvedEncoding;
            }
            resolvedEncoding = EncodingType.R50K_BASE;
            log.debug("model-context tokenizer encoding resolved: model={}, normalized={}, encoding={}",
                    safeModel(modelName), normalized, resolvedEncoding);
            return resolvedEncoding;
        }
        resolvedEncoding = EncodingType.CL100K_BASE;
        log.debug("model-context tokenizer encoding resolved: model={}, normalized={}, encoding={}",
                safeModel(modelName), normalized, resolvedEncoding);
        return resolvedEncoding;
    }

    private static String normalize(String modelName) {
        String lowered = modelName.trim().toLowerCase(Locale.ROOT);
        return lowered.replace('_', '-');
    }

    private static boolean matchesAnyPrefix(String text, String... prefixes) {
        for (String prefix : prefixes) {
            if (text.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String text, String... parts) {
        for (String part : parts) {
            if (text.contains(part)) {
                return true;
            }
        }
        return false;
    }

    private static String safeModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return "blank";
        }
        return modelName.trim();
    }
}
