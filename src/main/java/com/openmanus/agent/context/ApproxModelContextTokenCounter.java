package com.openmanus.agent.context;

import com.openmanus.aiframework.runtime.model.AiChatMessage;

/**
 * Current approximate token counter implementation.
 */
public final class ApproxModelContextTokenCounter implements ModelContextTokenCounter {

    private static final ApproxModelContextTokenCounter INSTANCE = new ApproxModelContextTokenCounter();

    private ApproxModelContextTokenCounter() {
    }

    public static ApproxModelContextTokenCounter getInstance() {
        return INSTANCE;
    }

    @Override
    public int estimateTokens(AiChatMessage message) {
        if (message == null) {
            return 0;
        }
        String text = extractText(message);
        if (text == null || text.isEmpty()) {
            return 8;
        }
        return 8 + (text.length() + 3) / 4;
    }

    private static String extractText(AiChatMessage message) {
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
}
