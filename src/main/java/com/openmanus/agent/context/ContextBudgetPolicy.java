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
 * Context budget policy that enforces history/total/approx-token limits.
 */
public final class ContextBudgetPolicy {

    private static final ContextBudgetPolicy DEFAULTS = new ContextBudgetPolicy(0, 0, 0);

    private final int maxHistoryMessages;
    private final int maxTotalMessages;
    private final int maxApproxTokens;
    private final ModelContextTokenCounter modelContextTokenCounter;

    public ContextBudgetPolicy(int maxHistoryMessages, int maxTotalMessages, int maxApproxTokens) {
        this(maxHistoryMessages, maxTotalMessages, maxApproxTokens, ApproxModelContextTokenCounter.getInstance());
    }

    public ContextBudgetPolicy(int maxHistoryMessages,
                               int maxTotalMessages,
                               int maxApproxTokens,
                               ModelContextTokenCounter modelContextTokenCounter) {
        this.maxHistoryMessages = Math.max(0, maxHistoryMessages);
        this.maxTotalMessages = Math.max(0, maxTotalMessages);
        this.maxApproxTokens = Math.max(0, maxApproxTokens);
        this.modelContextTokenCounter = modelContextTokenCounter == null
                ? ApproxModelContextTokenCounter.getInstance()
                : modelContextTokenCounter;
    }

    public List<AiChatMessage> trimHistory(List<AiChatMessage> history) {
        List<AiChatMessage> safeHistory = sanitize(history);
        if (safeHistory.isEmpty()) {
            return List.of();
        }
        if (maxHistoryMessages <= 0 || safeHistory.size() <= maxHistoryMessages) {
            return new ArrayList<>(safeHistory);
        }

        AiChatMessage firstSystemMessage = safeHistory.stream()
                .filter(message -> message.role() == AiChatMessage.Role.SYSTEM)
                .findFirst()
                .orElse(null);

        if (firstSystemMessage == null) {
            return tail(safeHistory, maxHistoryMessages);
        }
        if (maxHistoryMessages == 1) {
            return List.of(firstSystemMessage);
        }

        List<AiChatMessage> nonSystemMessages = safeHistory.stream()
                .filter(message -> message.role() != AiChatMessage.Role.SYSTEM)
                .collect(Collectors.toList());
        List<AiChatMessage> trimmed = new ArrayList<>();
        trimmed.add(firstSystemMessage);
        trimmed.addAll(tail(nonSystemMessages, maxHistoryMessages - 1));
        return trimmed;
    }

    public List<AiChatMessage> trimForTotalLimit(List<AiChatMessage> messages, AiChatMessage currentUserMessage) {
        List<AiChatMessage> safeMessages = sanitize(messages);
        if (safeMessages.isEmpty()) {
            return List.of();
        }
        if (maxTotalMessages <= 0 || safeMessages.size() <= maxTotalMessages) {
            return new ArrayList<>(safeMessages);
        }

        AiChatMessage firstSystemMessage = safeMessages.stream()
                .filter(message -> message.role() == AiChatMessage.Role.SYSTEM)
                .findFirst()
                .orElse(null);
        int userIndex = findMessageIndex(safeMessages, currentUserMessage);
        AiChatMessage currentUserAnchor = userIndex >= 0 ? safeMessages.get(userIndex) : currentUserMessage;
        AiChatMessage latestToolResult = findLatestToolResultMessage(safeMessages, userIndex);

        Set<AiChatMessage> selected = Collections.newSetFromMap(new IdentityHashMap<>());
        if (userIndex >= 0 && selected.size() < maxTotalMessages) {
            selected.add(currentUserAnchor);
        }
        if (latestToolResult != null
                && maxTotalMessages <= 2
                && selected.size() < maxTotalMessages) {
            selected.add(latestToolResult);
        }
        if (firstSystemMessage != null && selected.size() < maxTotalMessages) {
            selected.add(firstSystemMessage);
        }
        if (latestToolResult != null && selected.size() < maxTotalMessages) {
            selected.add(latestToolResult);
        }

        for (int i = safeMessages.size() - 1; i >= 0 && selected.size() < maxTotalMessages; i--) {
            selected.add(safeMessages.get(i));
        }

        List<AiChatMessage> result = new ArrayList<>();
        for (AiChatMessage message : safeMessages) {
            if (selected.contains(message)) {
                result.add(message);
            }
        }
        if (result.size() > maxTotalMessages) {
            return tail(result, maxTotalMessages);
        }
        return result;
    }

    public List<AiChatMessage> applyApproxTokenBudget(List<AiChatMessage> messages, AiChatMessage currentUserMessage) {
        List<AiChatMessage> safeMessages = sanitize(messages);
        if (safeMessages.isEmpty()) {
            return List.of();
        }
        if (maxApproxTokens <= 0) {
            return new ArrayList<>(safeMessages);
        }

        List<AiChatMessage> budgetedRuntimeMessages = ModelContextBudgeter.applyTokenBudget(
                safeMessages,
                currentUserMessage,
                maxApproxTokens,
                modelContextTokenCounter
        );
        if (budgetedRuntimeMessages == null || budgetedRuntimeMessages.isEmpty()) {
            return tail(safeMessages, 1);
        }
        return new ArrayList<>(budgetedRuntimeMessages);
    }

    public static ContextBudgetPolicy defaults() {
        return DEFAULTS;
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
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        for (int i = messages.size() - 1; i > currentUserIndex; i--) {
            AiChatMessage message = messages.get(i);
            if (message != null && message.role() == AiChatMessage.Role.TOOL) {
                return message;
            }
        }
        return null;
    }

    private static List<AiChatMessage> tail(List<AiChatMessage> source, int size) {
        if (source == null || source.isEmpty() || size <= 0) {
            return List.of();
        }
        int from = Math.max(0, source.size() - size);
        return new ArrayList<>(source.subList(from, source.size()));
    }

    private static List<AiChatMessage> sanitize(List<AiChatMessage> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
