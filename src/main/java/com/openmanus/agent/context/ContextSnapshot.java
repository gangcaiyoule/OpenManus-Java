package com.openmanus.agent.context;

import com.openmanus.aiframework.runtime.model.AiChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable context snapshot for one model round.
 * Keeps source messages and pre-split views for assembly.
 */
public record ContextSnapshot(
        List<AiChatMessage> fullMessages,
        AiChatMessage currentUserMessage,
        List<AiChatMessage> historicalMessages,
        List<AiChatMessage> currentTurnMessages
) {

    public ContextSnapshot {
        fullMessages = copy(fullMessages);
        historicalMessages = copy(historicalMessages);
        currentTurnMessages = copy(currentTurnMessages);
    }

    public static ContextSnapshot from(List<AiChatMessage> fullMessages, AiChatMessage currentUserMessage) {
        List<AiChatMessage> safeFullMessages = copy(fullMessages);
        if (safeFullMessages.isEmpty()) {
            return new ContextSnapshot(List.of(), currentUserMessage, List.of(), List.of());
        }

        int userIndex = findMessageIndex(safeFullMessages, currentUserMessage);
        if (userIndex < 0) {
            return new ContextSnapshot(safeFullMessages, currentUserMessage, safeFullMessages, List.of());
        }

        List<AiChatMessage> historicalMessages = new ArrayList<>(safeFullMessages.subList(0, userIndex));
        List<AiChatMessage> currentTurnMessages = new ArrayList<>(safeFullMessages.subList(userIndex, safeFullMessages.size()));
        return new ContextSnapshot(safeFullMessages, currentUserMessage, historicalMessages, currentTurnMessages);
    }

    private static int findMessageIndex(List<AiChatMessage> messages, AiChatMessage target) {
        if (messages == null || messages.isEmpty() || target == null) {
            return -1;
        }
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) == target) {
                return i;
            }
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (target.equals(messages.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static List<AiChatMessage> copy(List<AiChatMessage> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return List.copyOf(source);
    }
}
