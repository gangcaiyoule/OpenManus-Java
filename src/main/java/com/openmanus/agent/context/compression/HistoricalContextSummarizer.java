package com.openmanus.agent.context.compression;

import com.openmanus.aiframework.runtime.model.AiChatMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds one compact memory card from trimmed-out historical messages.
 * This keeps a short-term window plus a tiny set of key historical anchors.
 */
public final class HistoricalContextSummarizer {

    private static final int MAX_SUMMARY_CHARS = 120;
    private static final int MAX_TOOL_ITEMS = 2;
    private static final Pattern ARTIFACT_ID_PATTERN = Pattern.compile("artifactId=([^\\s]+)");
    private static final Pattern TOOL_NAME_PATTERN = Pattern.compile("tool=([^\\s]+)");

    public List<AiChatMessage> inject(List<AiChatMessage> originalHistory, List<AiChatMessage> retainedHistory) {
        List<AiChatMessage> safeOriginal = safeCopy(originalHistory);
        List<AiChatMessage> safeRetained = safeCopy(retainedHistory);
        if (safeOriginal.isEmpty() || safeRetained.isEmpty() || safeRetained.size() >= safeOriginal.size()) {
            return safeRetained;
        }

        List<AiChatMessage> dropped = collectDroppedMessages(safeOriginal, safeRetained);
        if (dropped.isEmpty()) {
            return safeRetained;
        }

        String summary = render(dropped);
        if (summary == null) {
            return safeRetained;
        }

        List<AiChatMessage> enriched = new ArrayList<>(safeRetained.size() + 1);
        int insertIndex = shouldInsertAfterLeadingSystem(safeRetained) ? 1 : 0;
        enriched.addAll(safeRetained.subList(0, insertIndex));
        enriched.add(AiChatMessage.assistant(summary));
        enriched.addAll(safeRetained.subList(insertIndex, safeRetained.size()));
        return enriched;
    }

    static String render(List<AiChatMessage> droppedMessages) {
        List<AiChatMessage> safeDropped = safeCopy(droppedMessages);
        if (safeDropped.isEmpty()) {
            return null;
        }

        String lastUserIntent = findLastUserIntent(safeDropped);
        String lastAssistantOutcome = findLastAssistantOutcome(safeDropped);
        List<String> recentToolObservations = findRecentToolObservations(safeDropped);

        if (lastUserIntent == null && lastAssistantOutcome == null && recentToolObservations.isEmpty()) {
            return null;
        }

        String toolBlock = recentToolObservations.isEmpty()
                ? "- n/a"
                : String.join("\n", recentToolObservations);
        return """
                [Historical Key Memory]
                lastUserIntent: %s
                lastAssistantOutcome: %s
                recentToolObservations:
                %s
                """.formatted(
                fallback(lastUserIntent),
                fallback(lastAssistantOutcome),
                toolBlock
        );
    }

    private static List<AiChatMessage> collectDroppedMessages(List<AiChatMessage> originalHistory,
                                                              List<AiChatMessage> retainedHistory) {
        Set<AiChatMessage> retained = Collections.newSetFromMap(new IdentityHashMap<>());
        retained.addAll(retainedHistory);

        List<AiChatMessage> dropped = new ArrayList<>();
        for (AiChatMessage message : originalHistory) {
            if (!retained.contains(message)) {
                dropped.add(message);
            }
        }
        return dropped;
    }

    private static boolean shouldInsertAfterLeadingSystem(List<AiChatMessage> retainedHistory) {
        return !retainedHistory.isEmpty() && retainedHistory.getFirst().role() == AiChatMessage.Role.SYSTEM;
    }

    private static String findLastUserIntent(List<AiChatMessage> droppedMessages) {
        for (int i = droppedMessages.size() - 1; i >= 0; i--) {
            AiChatMessage message = droppedMessages.get(i);
            if (message.role() == AiChatMessage.Role.USER) {
                return normalizeContent(message.content());
            }
        }
        return null;
    }

    private static String findLastAssistantOutcome(List<AiChatMessage> droppedMessages) {
        for (int i = droppedMessages.size() - 1; i >= 0; i--) {
            AiChatMessage message = droppedMessages.get(i);
            if (message.role() != AiChatMessage.Role.ASSISTANT) {
                continue;
            }
            String content = message.content();
            if (content == null || content.isBlank()) {
                continue;
            }
            if (content.contains("[Task State]") || content.contains("[Historical Key Memory]")) {
                continue;
            }
            return normalizeContent(content);
        }
        return null;
    }

    private static List<String> findRecentToolObservations(List<AiChatMessage> droppedMessages) {
        List<String> observations = new ArrayList<>();
        for (int i = droppedMessages.size() - 1; i >= 0 && observations.size() < MAX_TOOL_ITEMS; i--) {
            AiChatMessage message = droppedMessages.get(i);
            String summary = summarizeToolMessage(message);
            if (summary != null) {
                observations.add("- " + summary);
            }
        }
        Collections.reverse(observations);
        return observations;
    }

    private static String summarizeToolMessage(AiChatMessage message) {
        if (message.role() == AiChatMessage.Role.TOOL) {
            String toolName = normalizeInline(message.name());
            String detail = normalizeContent(message.content());
            if (toolName == null && detail == null) {
                return null;
            }
            if (toolName == null) {
                return detail;
            }
            if (detail == null) {
                return "tool=" + toolName;
            }
            return "tool=" + toolName + "; detail=" + detail;
        }

        if (message.role() != AiChatMessage.Role.ASSISTANT) {
            return null;
        }

        String content = message.content();
        if (content == null || content.isBlank() || !content.contains("[Tool Result")) {
            return null;
        }

        String toolName = extractPatternGroup(content, TOOL_NAME_PATTERN);
        String artifactId = extractPatternGroup(content, ARTIFACT_ID_PATTERN);
        String summary = normalizeContent(content);
        List<String> parts = new ArrayList<>();
        if (toolName != null) {
            parts.add("tool=" + toolName);
        }
        if (artifactId != null) {
            parts.add("artifactId=" + artifactId);
        }
        if (summary != null) {
            parts.add("detail=" + summary);
        }
        return parts.isEmpty() ? null : String.join("; ", parts);
    }

    private static String extractPatternGroup(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content == null ? "" : content);
        if (!matcher.find()) {
            return null;
        }
        return normalizeInline(matcher.group(1));
    }

    private static String normalizeContent(String content) {
        if (content == null) {
            return null;
        }
        String normalized = content
                .replace('\r', '\n')
                .replace('\t', ' ')
                .replaceAll("\\R+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.length() <= MAX_SUMMARY_CHARS
                ? normalized
                : normalized.substring(0, MAX_SUMMARY_CHARS - 3) + "...";
    }

    private static String normalizeInline(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String fallback(String value) {
        return value == null ? "n/a" : value;
    }

    private static List<AiChatMessage> safeCopy(List<AiChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(messages);
    }
}
