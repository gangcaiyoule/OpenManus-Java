package com.openmanus.agent.context;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.openmanus.aiframework.runtime.model.AiChatMessage;
import com.openmanus.aiframework.runtime.model.AiToolCall;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token counter backed by model tokenizer encoding.
 */
public final class ModelTokenizerModelContextTokenCounter implements ModelContextTokenCounter {

    private static final ModelTokenizerModelContextTokenCounter INSTANCE =
            new ModelTokenizerModelContextTokenCounter(loadEncoding(EncodingType.CL100K_BASE), EncodingType.CL100K_BASE);
    private static final Map<EncodingType, ModelTokenizerModelContextTokenCounter> CACHE =
            new ConcurrentHashMap<>();
    private static final int MESSAGE_BASE_TOKENS = 4;

    private final Encoding encoding;
    private final EncodingType encodingType;

    private ModelTokenizerModelContextTokenCounter(Encoding encoding, EncodingType encodingType) {
        if (encoding == null) {
            throw new IllegalStateException("model tokenizer encoding is unavailable");
        }
        this.encoding = encoding;
        this.encodingType = encodingType == null ? EncodingType.CL100K_BASE : encodingType;
    }

    public static ModelTokenizerModelContextTokenCounter getInstance() {
        return INSTANCE;
    }

    public static ModelTokenizerModelContextTokenCounter forModel(String modelName) {
        EncodingType encodingType = ModelTokenizerEncodingMapper.resolve(modelName);
        return CACHE.computeIfAbsent(
                encodingType,
                type -> new ModelTokenizerModelContextTokenCounter(loadEncoding(type), type)
        );
    }

    @Override
    public int estimateTokens(AiChatMessage message) {
        if (message == null) {
            return 0;
        }
        int total = MESSAGE_BASE_TOKENS;
        total += countTokens(message.role().name().toLowerCase());
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

    int countTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return encoding.countTokens(text);
    }

    EncodingType encodingType() {
        return encodingType;
    }

    private static Encoding loadEncoding(EncodingType encodingType) {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        return registry.getEncoding(encodingType == null ? EncodingType.CL100K_BASE : encodingType);
    }
}
