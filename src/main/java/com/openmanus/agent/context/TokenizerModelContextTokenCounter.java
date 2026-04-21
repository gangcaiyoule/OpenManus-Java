package com.openmanus.agent.context;

import com.openmanus.aiframework.runtime.model.AiChatMessage;
import com.openmanus.aiframework.runtime.model.AiToolCall;

import java.util.List;

/**
 * Lightweight tokenizer-style counter.
 * Uses unicode-aware token boundaries to provide a more stable estimate than char/4 approximation.
 */
public final class TokenizerModelContextTokenCounter implements ModelContextTokenCounter {

    private static final TokenizerModelContextTokenCounter INSTANCE = new TokenizerModelContextTokenCounter();
    private static final int MESSAGE_BASE_TOKENS = 4;

    private TokenizerModelContextTokenCounter() {
    }

    public static TokenizerModelContextTokenCounter getInstance() {
        return INSTANCE;
    }

    @Override
    public int estimateTokens(AiChatMessage message) {
        if (message == null) {
            return 0;
        }
        int total = MESSAGE_BASE_TOKENS;
        total += estimateTextTokens(message.content());
        total += estimateTextTokens(message.name());
        total += estimateTextTokens(message.toolCallId());

        List<AiToolCall> toolCalls = message.toolCalls();
        if (toolCalls != null && !toolCalls.isEmpty()) {
            for (AiToolCall toolCall : toolCalls) {
                total += estimateToolCallTokens(toolCall);
            }
        }
        return Math.max(1, total);
    }

    private static int estimateToolCallTokens(AiToolCall toolCall) {
        if (toolCall == null) {
            return 1;
        }
        int total = 2;
        total += estimateTextTokens(toolCall.id());
        total += estimateTextTokens(toolCall.name());
        total += estimateTextTokens(toolCall.arguments());
        return Math.max(1, total);
    }

    static int estimateTextTokens(String text) {
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
        // Keep a lightweight approximation of subword splits for long alphanumeric spans.
        return Math.max(1, (codePointCount + 3) / 4);
    }

    private static boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }
}
