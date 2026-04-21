package com.openmanus.agent.context;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Token-budget-based model context selector.
 * Keeps critical anchors first, then fills with newest messages under budget.
 */
public final class ModelContextBudgeter {

    private ModelContextBudgeter() {
    }

    public static List<ChatMessage> applyApproxTokenBudget(List<ChatMessage> messages,
                                                           UserMessage currentUserMessage,
                                                           int maxApproxTokens) {
        if (messages == null || messages.isEmpty() || maxApproxTokens <= 0) {
            return messages;
        }

        SystemMessage firstSystemMessage = messages.stream()
                .filter(message -> message instanceof SystemMessage)
                .map(message -> (SystemMessage) message)
                .findFirst()
                .orElse(null);
        int userIndex = findMessageIndexByIdentity(messages, currentUserMessage);
        ChatMessage latestToolResult = findLatestToolResultMessage(messages, userIndex);

        List<ChatMessage> fixed = new ArrayList<>();
        Set<ChatMessage> selected = Collections.newSetFromMap(new IdentityHashMap<>());

        if (currentUserMessage != null) {
            selected.add(currentUserMessage);
        }
        if (firstSystemMessage != null) {
            selected.add(firstSystemMessage);
        }
        if (latestToolResult != null) {
            selected.add(latestToolResult);
        }
        for (ChatMessage message : messages) {
            if (selected.contains(message)) {
                fixed.add(message);
            }
        }

        int fixedTokens = estimateTokens(fixed);
        if (fixedTokens > maxApproxTokens) {
            List<ChatMessage> minimal = minimalCriticalSet(currentUserMessage, latestToolResult, firstSystemMessage, maxApproxTokens);
            if (!minimal.isEmpty()) {
                return minimal;
            }
            return tail(messages, 1);
        }

        selected.clear();
        for (ChatMessage message : fixed) {
            selected.add(message);
        }
        int usedTokens = fixedTokens;

        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage candidate = messages.get(i);
            if (selected.contains(candidate)) {
                continue;
            }
            int candidateTokens = estimateTokens(candidate);
            if (usedTokens + candidateTokens > maxApproxTokens) {
                continue;
            }
            selected.add(candidate);
            usedTokens += candidateTokens;
        }

        List<ChatMessage> result = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (selected.contains(message)) {
                result.add(message);
            }
        }
        return result.isEmpty() ? tail(messages, 1) : result;
    }

    private static int estimateTokens(List<ChatMessage> messages) {
        int total = 0;
        for (ChatMessage message : messages) {
            total += estimateTokens(message);
        }
        return total;
    }

    private static int estimateTokens(ChatMessage message) {
        if (message == null) {
            return 0;
        }
        String text = extractText(message);
        if (text == null || text.isEmpty()) {
            return 8;
        }
        return 8 + (text.length() + 3) / 4;
    }

    private static String extractText(ChatMessage message) {
        if (message instanceof SystemMessage systemMessage) {
            return systemMessage.text();
        }
        if (message instanceof UserMessage userMessage) {
            return userMessage.singleText();
        }
        if (message instanceof ToolExecutionResultMessage toolResultMessage) {
            return toolResultMessage.text();
        }
        if (message instanceof AiMessage aiMessage) {
            String text = aiMessage.text();
            if (text != null && !text.isBlank()) {
                return text;
            }
            if (aiMessage.hasToolExecutionRequests()) {
                return aiMessage.toolExecutionRequests().toString();
            }
            return "";
        }
        return message.toString();
    }

    private static int findMessageIndexByIdentity(List<ChatMessage> messages, ChatMessage target) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) == target) {
                return i;
            }
        }
        return -1;
    }

    private static ChatMessage findLatestToolResultMessage(List<ChatMessage> messages, int currentUserIndex) {
        for (int i = messages.size() - 1; i > currentUserIndex; i--) {
            ChatMessage message = messages.get(i);
            if (message instanceof ToolExecutionResultMessage) {
                return message;
            }
        }
        return null;
    }

    private static List<ChatMessage> tail(List<ChatMessage> source, int size) {
        if (size <= 0 || source.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, source.size() - size);
        return new ArrayList<>(source.subList(from, source.size()));
    }

    private static List<ChatMessage> minimalCriticalSet(UserMessage currentUserMessage,
                                                        ChatMessage latestToolResult,
                                                        SystemMessage firstSystemMessage,
                                                        int maxApproxTokens) {
        List<ChatMessage> prioritized = new ArrayList<>(3);
        if (currentUserMessage != null) {
            prioritized.add(currentUserMessage);
        }
        if (latestToolResult != null) {
            prioritized.add(latestToolResult);
        }
        if (firstSystemMessage != null) {
            prioritized.add(firstSystemMessage);
        }

        List<ChatMessage> result = new ArrayList<>(3);
        int used = 0;
        for (ChatMessage message : prioritized) {
            int tokens = estimateTokens(message);
            if (!result.isEmpty() && used + tokens > maxApproxTokens) {
                continue;
            }
            if (result.isEmpty() && used + tokens > maxApproxTokens) {
                result.add(message);
                return result;
            }
            if (used + tokens <= maxApproxTokens) {
                result.add(message);
                used += tokens;
            }
        }
        return result;
    }
}
