package com.openmanus.agent.context.token;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.openmanus.aiframework.runtime.model.AiChatMessage;
import com.openmanus.aiframework.runtime.model.AiToolCall;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * Token counter used by model-context budgeting.
 */
@Slf4j
public final class ModelContextTokenCounter implements ToIntFunction<AiChatMessage> {

    public static final String MODE_APPROX = "approx";
    public static final String MODE_TOKENIZER = "tokenizer";

    private static final ModelContextTokenCounter APPROX =
            new ModelContextTokenCounter(CounterMode.APPROX, ModelContextTokenCounter::estimateApproxTokens, "");
    private static final ModelContextTokenCounter LIGHTWEIGHT =
            new ModelContextTokenCounter(
                    CounterMode.LIGHTWEIGHT_TOKENIZER,
                    ModelContextTokenCounter::estimateLightweightTokens,
                    ""
            );
    private static final int APPROX_EMPTY_MESSAGE_TOKENS = 8;
    private static final int LIGHTWEIGHT_MESSAGE_BASE_TOKENS = 4;

    private final CounterMode counterMode;
    private final ToIntFunction<AiChatMessage> estimator;
    private final String encodingTypeName;

    private ModelContextTokenCounter(CounterMode counterMode,
                                     ToIntFunction<AiChatMessage> estimator,
                                     String encodingTypeName) {
        if (estimator == null) {
            throw new IllegalArgumentException("token estimator must not be null");
        }
        this.counterMode = counterMode == null ? CounterMode.APPROX : counterMode;
        this.estimator = estimator;
        this.encodingTypeName = encodingTypeName == null ? "" : encodingTypeName;
    }

    public static ModelContextTokenCounter approx() {
        return APPROX;
    }

    static ModelContextTokenCounter lightweight() {
        return LIGHTWEIGHT;
    }

    public static ModelContextTokenCounter create(String mode) {
        return create(mode, (String) null);
    }

    public static ModelContextTokenCounter create(String mode, String modelName) {
        return create(
                mode,
                modelName,
                () -> forModel(modelName),
                ModelContextTokenCounter::lightweight
        );
    }

    static ModelContextTokenCounter create(String mode,
                                           String modelName,
                                           Supplier<ModelContextTokenCounter> modelTokenizerSupplier,
                                           Supplier<ModelContextTokenCounter> lightweightTokenizerSupplier) {
        String normalized = normalizeMode(mode);
        if (MODE_TOKENIZER.equals(normalized)) {
            return createTokenizerCounterOrFallback(modelName, modelTokenizerSupplier, lightweightTokenizerSupplier);
        } else if (!MODE_APPROX.equals(normalized)) {
            log.warn("unknown model-context token-count mode '{}', fallback to '{}'", mode, MODE_APPROX);
        }
        log.info("model-context token counter selected: mode={}, model={}, fallbackStage=approx, counter={}",
                MODE_APPROX, safeModel(modelName), APPROX.counterName());
        return APPROX;
    }

    public static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return MODE_APPROX;
        }
        return mode.trim().toLowerCase(Locale.ROOT);
    }

    static ModelContextTokenCounter forModel(String modelName) {
        return JtokkitEstimator.forModel(modelName);
    }

    @Override
    public int applyAsInt(AiChatMessage message) {
        return estimateTokens(message);
    }

    public int estimateTokens(AiChatMessage message) {
        return estimator.applyAsInt(message);
    }

    public int estimateTokens(List<AiChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (AiChatMessage message : messages) {
            total += estimateTokens(message);
        }
        return total;
    }

    String encodingTypeName() {
        return encodingTypeName;
    }

    private String counterName() {
        return counterMode.counterName;
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
                        MODE_TOKENIZER, safeModel(modelName), tokenizerCounter.counterName());
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
                        MODE_TOKENIZER, safeModel(modelName), tokenizerCounter.counterName());
                return tokenizerCounter;
            }
            log.warn("lightweight tokenizer counter is unavailable, fallback to '{}'", MODE_APPROX);
        } catch (RuntimeException | LinkageError ex) {
            log.warn("failed to initialize lightweight tokenizer counter, fallback to '{}': {}",
                    MODE_APPROX, ex.getMessage());
        }
        log.info("model-context token counter selected: mode={}, model={}, fallbackStage=approx, counter={}",
                MODE_TOKENIZER, safeModel(modelName), APPROX.counterName());
        return APPROX;
    }

    private static int estimateApproxTokens(AiChatMessage message) {
        if (message == null) {
            return 0;
        }
        String text = extractApproxText(message);
        if (text == null || text.isEmpty()) {
            return APPROX_EMPTY_MESSAGE_TOKENS;
        }
        return APPROX_EMPTY_MESSAGE_TOKENS + (text.length() + 3) / 4;
    }

    private static String extractApproxText(AiChatMessage message) {
        if (message.role() == AiChatMessage.Role.ASSISTANT) {
            String text = message.content();
            if (text != null && !text.isBlank()) {
                return text;
            }
            if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
                return message.toolCalls().toString();
            }
            return "";
        }
        return message.content();
    }

    private static int estimateLightweightTokens(AiChatMessage message) {
        if (message == null) {
            return 0;
        }
        int total = LIGHTWEIGHT_MESSAGE_BASE_TOKENS;
        total += estimateLightweightTextTokens(message.content());
        total += estimateLightweightTextTokens(message.name());
        total += estimateLightweightTextTokens(message.toolCallId());

        List<AiToolCall> toolCalls = message.toolCalls();
        if (toolCalls != null && !toolCalls.isEmpty()) {
            for (AiToolCall toolCall : toolCalls) {
                total += estimateLightweightToolCallTokens(toolCall);
            }
        }
        return Math.max(1, total);
    }

    private static int estimateLightweightToolCallTokens(AiToolCall toolCall) {
        if (toolCall == null) {
            return 1;
        }
        int total = 2;
        total += estimateLightweightTextTokens(toolCall.id());
        total += estimateLightweightTextTokens(toolCall.name());
        total += estimateLightweightTextTokens(toolCall.arguments());
        return Math.max(1, total);
    }

    private static int estimateLightweightTextTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int tokens = 0;
        boolean inWord = false;
        int wordCodePoints = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);

            if (Character.isWhitespace(codePoint)) {
                if (inWord) {
                    tokens += estimateWordPieceTokens(wordCodePoints);
                    inWord = false;
                    wordCodePoints = 0;
                }
                continue;
            }
            if (isCjk(codePoint)) {
                if (inWord) {
                    tokens += estimateWordPieceTokens(wordCodePoints);
                    inWord = false;
                    wordCodePoints = 0;
                }
                tokens++;
                continue;
            }
            if (Character.isLetterOrDigit(codePoint) || codePoint == '_') {
                inWord = true;
                wordCodePoints++;
                continue;
            }
            if (inWord) {
                tokens += estimateWordPieceTokens(wordCodePoints);
                inWord = false;
                wordCodePoints = 0;
            }
            tokens++;
        }
        if (inWord) {
            tokens += estimateWordPieceTokens(wordCodePoints);
        }
        return tokens;
    }

    private static int estimateWordPieceTokens(int codePointCount) {
        if (codePointCount <= 0) {
            return 0;
        }
        return Math.max(1, (codePointCount + 3) / 4);
    }

    private static boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    private static String safeModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return "blank";
        }
        return modelName.trim();
    }

    private enum CounterMode {
        APPROX("approx"),
        MODEL_TOKENIZER("model_tokenizer"),
        LIGHTWEIGHT_TOKENIZER("lightweight_tokenizer");

        private final String counterName;

        CounterMode(String counterName) {
            this.counterName = counterName;
        }
    }

    private static final class JtokkitEstimator implements ToIntFunction<AiChatMessage> {

        private static final Map<EncodingType, ModelContextTokenCounter> CACHE =
                new ConcurrentHashMap<>();
        private static final int MESSAGE_BASE_TOKENS = 4;

        private final Encoding encoding;

        private JtokkitEstimator(Encoding encoding) {
            if (encoding == null) {
                throw new IllegalStateException("model tokenizer encoding is unavailable");
            }
            this.encoding = encoding;
        }

        private static ModelContextTokenCounter forModel(String modelName) {
            EncodingType encodingType = resolveEncoding(modelName);
            return CACHE.computeIfAbsent(
                    encodingType,
                    type -> new ModelContextTokenCounter(
                            CounterMode.MODEL_TOKENIZER,
                            new JtokkitEstimator(loadEncoding(type)),
                            type.name()
                    )
            );
        }

        @Override
        public int applyAsInt(AiChatMessage message) {
            if (message == null) {
                return 0;
            }
            int total = MESSAGE_BASE_TOKENS;
            total += countTokens(message.role().name().toLowerCase(Locale.ROOT));
            total += countTokens(message.content());
            total += countTokens(message.name());
            total += countTokens(message.toolCallId());
            total += estimateToolCallTokens(message.toolCalls());
            return Math.max(1, total);
        }

        private int estimateToolCallTokens(List<AiToolCall> toolCalls) {
            if (toolCalls == null || toolCalls.isEmpty()) {
                return 0;
            }
            int total = 0;
            for (AiToolCall toolCall : toolCalls) {
                if (toolCall == null) {
                    continue;
                }
                total += 3;
                total += countTokens(toolCall.id());
                total += countTokens(toolCall.name());
                total += countTokens(toolCall.arguments());
            }
            return total;
        }

        private int countTokens(String text) {
            if (text == null || text.isBlank()) {
                return 0;
            }
            return encoding.countTokens(text);
        }

        private static Encoding loadEncoding(EncodingType encodingType) {
            EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
            return registry.getEncoding(encodingType == null ? EncodingType.CL100K_BASE : encodingType);
        }

        private static EncodingType resolveEncoding(String modelName) {
            EncodingType resolvedEncoding;
            if (modelName == null || modelName.isBlank()) {
                resolvedEncoding = EncodingType.CL100K_BASE;
                log.debug("model-context tokenizer encoding resolved: model={}, normalized={}, encoding={}",
                        safeModel(modelName), "blank", resolvedEncoding);
                return resolvedEncoding;
            }
            String normalized = normalizeModelName(modelName);

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

        private static String normalizeModelName(String modelName) {
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
    }
}
