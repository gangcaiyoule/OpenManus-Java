package com.openmanus.agent.context;

import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Creates runtime token-counter implementation by configured mode.
 */
@Slf4j
public final class ModelContextTokenCounterFactory {

    public static final String MODE_APPROX = "approx";
    public static final String MODE_TOKENIZER = "tokenizer";

    private ModelContextTokenCounterFactory() {
    }

    public static ModelContextTokenCounter create(String mode) {
        return create(mode, (String) null);
    }

    public static ModelContextTokenCounter create(String mode, String modelName) {
        return create(
                mode,
                modelName,
                () -> ModelTokenizerModelContextTokenCounter.forModel(modelName),
                TokenizerModelContextTokenCounter::getInstance
        );
    }

    static ModelContextTokenCounter create(String mode,
                                           String modelName,
                                           Supplier<ModelContextTokenCounter> modelTokenizerSupplier,
                                           Supplier<ModelContextTokenCounter> lightweightTokenizerSupplier) {
        return createInternal(
                mode,
                modelName,
                modelTokenizerSupplier,
                lightweightTokenizerSupplier
        );
    }

    static ModelContextTokenCounter create(String mode, Supplier<ModelContextTokenCounter> tokenizerSupplier) {
        return create(mode, null, tokenizerSupplier, TokenizerModelContextTokenCounter::getInstance);
    }

    static ModelContextTokenCounter create(String mode,
                                           Supplier<ModelContextTokenCounter> modelTokenizerSupplier,
                                           Supplier<ModelContextTokenCounter> lightweightTokenizerSupplier) {
        return createInternal(mode, null, modelTokenizerSupplier, lightweightTokenizerSupplier);
    }

    private static ModelContextTokenCounter createInternal(
            String mode,
            String modelName,
            Supplier<ModelContextTokenCounter> modelTokenizerSupplier,
            Supplier<ModelContextTokenCounter> lightweightTokenizerSupplier) {
        String normalized = normalizeMode(mode);
        if (MODE_TOKENIZER.equals(normalized)) {
            return createTokenizerCounterOrFallback(modelName, modelTokenizerSupplier, lightweightTokenizerSupplier);
        } else if (!MODE_APPROX.equals(normalized)) {
            log.warn("unknown model-context token-count mode '{}', fallback to '{}'",
                    mode, MODE_APPROX);
        }
        log.info("model-context token counter selected: mode={}, model={}, fallbackStage=approx, counter={}",
                MODE_APPROX, safeModel(modelName), ApproxModelContextTokenCounter.class.getSimpleName());
        return ApproxModelContextTokenCounter.getInstance();
    }

    public static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return MODE_APPROX;
        }
        return mode.trim().toLowerCase(Locale.ROOT);
    }

    private static ModelContextTokenCounter createTokenizerCounterOrFallback(
            String modelName,
            Supplier<ModelContextTokenCounter> modelTokenizerSupplier,
            Supplier<ModelContextTokenCounter> lightweightTokenizerSupplier) {
        try {
            ModelContextTokenCounter tokenizerCounter = modelTokenizerSupplier == null
                    ? null
                    : modelTokenizerSupplier.get();
            if (tokenizerCounter != null) {
                log.info("model-context token counter selected: mode={}, model={}, fallbackStage=model_tokenizer, counter={}",
                        MODE_TOKENIZER, safeModel(modelName), tokenizerCounter.getClass().getSimpleName());
                return tokenizerCounter;
            }
            log.warn("model tokenizer counter is unavailable, fallback to lightweight tokenizer counter");
        } catch (RuntimeException | LinkageError ex) {
            log.warn("failed to initialize model tokenizer counter, fallback to lightweight tokenizer counter: {}",
                    ex.getMessage());
        }
        try {
            ModelContextTokenCounter tokenizerCounter = lightweightTokenizerSupplier == null
                    ? null
                    : lightweightTokenizerSupplier.get();
            if (tokenizerCounter != null) {
                log.info("model-context token counter selected: mode={}, model={}, fallbackStage=lightweight_tokenizer, counter={}",
                        MODE_TOKENIZER, safeModel(modelName), tokenizerCounter.getClass().getSimpleName());
                return tokenizerCounter;
            }
            log.warn("lightweight tokenizer counter is unavailable, fallback to '{}'", MODE_APPROX);
        } catch (RuntimeException | LinkageError ex) {
            log.warn("failed to initialize lightweight tokenizer counter, fallback to '{}': {}",
                    MODE_APPROX, ex.getMessage());
        }
        log.info("model-context token counter selected: mode={}, model={}, fallbackStage=approx, counter={}",
                MODE_TOKENIZER, safeModel(modelName), ApproxModelContextTokenCounter.class.getSimpleName());
        return ApproxModelContextTokenCounter.getInstance();
    }

    private static String safeModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return "blank";
        }
        return modelName.trim();
    }
}
