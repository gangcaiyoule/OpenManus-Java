package com.openmanus.agent.context;

import com.openmanus.aiframework.runtime.model.AiChatMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Token-budget-based model context selector.
 * Keeps critical anchors first, then fills with newest messages under budget.
 */
public final class ModelContextBudgeter {

    private ModelContextBudgeter() {
    }

    public static List<AiChatMessage> applyApproxTokenBudget(List<AiChatMessage> messages,
                                                              AiChatMessage currentUserMessage,
                                                              int maxApproxTokens) {
        return applyTokenBudget(
                messages,
                currentUserMessage,
                maxApproxTokens,
                ApproxModelContextTokenCounter.getInstance()
        );
    }

    public static List<AiChatMessage> applyTokenBudget(List<AiChatMessage> messages,
                                                       AiChatMessage currentUserMessage,
                                                       int maxTokens,
                                                       ModelContextTokenCounter tokenCounter) {
        List<AiChatMessage> safeMessages = sanitize(messages);
        if (safeMessages.isEmpty() || maxTokens <= 0) {
            return safeMessages;
        }
        if (tokenCounter == null) {
            tokenCounter = ApproxModelContextTokenCounter.getInstance();
        }

        AiChatMessage firstSystemMessage = safeMessages.stream()
                .filter(message -> message.role() == AiChatMessage.Role.SYSTEM)
                .findFirst()
                .orElse(null);
        int userIndex = findMessageIndex(safeMessages, currentUserMessage);
        AiChatMessage currentUserAnchor = userIndex >= 0 ? safeMessages.get(userIndex) : currentUserMessage;
        AiChatMessage latestToolResult = findLatestToolResultMessage(safeMessages, userIndex);

        List<AiChatMessage> fixed = new ArrayList<>();
        Set<AiChatMessage> selected = Collections.newSetFromMap(new IdentityHashMap<>());

        if (currentUserAnchor != null) {
            selected.add(currentUserAnchor);
        }
        if (firstSystemMessage != null) {
            selected.add(firstSystemMessage);
        }
        if (latestToolResult != null) {
            selected.add(latestToolResult);
        }
        for (AiChatMessage message : safeMessages) {
            if (selected.contains(message)) {
                fixed.add(message);
            }
        }

        int fixedTokens = tokenCounter.estimateTokens(fixed);
        if (fixedTokens > maxTokens) {
            List<AiChatMessage> minimal = minimalCriticalSet(
                    currentUserAnchor, latestToolResult, firstSystemMessage, maxTokens, tokenCounter);
            if (!minimal.isEmpty()) {
                return minimal;
            }
            return tail(safeMessages, 1);
        }

        selected.clear();
        for (AiChatMessage message : fixed) {
            selected.add(message);
        }
        int usedTokens = fixedTokens;

        for (int i = safeMessages.size() - 1; i >= 0; i--) {
            AiChatMessage candidate = safeMessages.get(i);
            if (selected.contains(candidate)) {
                continue;
            }
            int candidateTokens = tokenCounter.estimateTokens(candidate);
            if (usedTokens + candidateTokens > maxTokens) {
                continue;
            }
            selected.add(candidate);
            usedTokens += candidateTokens;
        }

        List<AiChatMessage> result = new ArrayList<>();
        for (AiChatMessage message : safeMessages) {
            if (selected.contains(message)) {
                result.add(message);
            }
        }
        return result.isEmpty() ? tail(safeMessages, 1) : result;
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

    private static AiChatMessage findLatestToolResultMessage(List<AiChatMessage> messages, int currentUserIndex) {
        for (int i = messages.size() - 1; i > currentUserIndex; i--) {
            AiChatMessage message = messages.get(i);
            if (message != null && message.role() == AiChatMessage.Role.TOOL) {
                return message;
            }
        }
        return null;
    }

    private static List<AiChatMessage> tail(List<AiChatMessage> source, int size) {
        if (size <= 0 || source.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, source.size() - size);
        return new ArrayList<>(source.subList(from, source.size()));
    }

    private static List<AiChatMessage> minimalCriticalSet(AiChatMessage currentUserMessage,
                                                           AiChatMessage latestToolResult,
                                                           AiChatMessage firstSystemMessage,
                                                           int maxTokens,
                                                           ModelContextTokenCounter tokenCounter) {
        List<AiChatMessage> prioritized = new ArrayList<>(3);
        if (currentUserMessage != null) {
            prioritized.add(currentUserMessage);
        }
        if (latestToolResult != null) {
            prioritized.add(latestToolResult);
        }
        if (firstSystemMessage != null) {
            prioritized.add(firstSystemMessage);
        }

        List<AiChatMessage> result = new ArrayList<>(3);
        int used = 0;
        for (AiChatMessage message : prioritized) {
            int tokens = tokenCounter.estimateTokens(message);
            if (!result.isEmpty() && used + tokens > maxTokens) {
                continue;
            }
            if (result.isEmpty() && used + tokens > maxTokens) {
                result.add(message);
                return result;
            }
            if (used + tokens <= maxTokens) {
                result.add(message);
                used += tokens;
            }
        }
        return result;
    }

    private static List<AiChatMessage> sanitize(List<AiChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return messages.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
