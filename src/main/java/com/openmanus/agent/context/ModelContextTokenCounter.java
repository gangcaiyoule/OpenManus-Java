package com.openmanus.agent.context;

import com.openmanus.aiframework.runtime.model.AiChatMessage;

import java.util.List;

/**
 * Pluggable token counter contract for model-context budgeting.
 */
public interface ModelContextTokenCounter {

    int estimateTokens(AiChatMessage message);

    default int estimateTokens(List<AiChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (AiChatMessage message : messages) {
            total += estimateTokens(message);
        }
        return total;
    }
}
