package com.openmanus.agent.context;

import com.openmanus.aiframework.runtime.model.AiChatMessage;
import com.openmanus.aiframework.runtime.model.AiToolCall;
import com.openmanus.aiframework.runtime.ToolResultArtifactRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Selects indexed tool-result artifacts to rehydrate into the next model round.
 */
public final class IndexedRehydrateSelector {

    private static final Pattern ARTIFACT_ID_PATTERN = Pattern.compile("sha256:[0-9a-fA-F]{64}");

    private IndexedRehydrateSelector() {
    }

    public static List<ToolResultArtifactRef> select(
            List<ToolResultArtifactRef> refs,
            List<AiChatMessage> modelMessages,
            AiChatMessage currentUserMessage,
            int maxCount) {
        if (refs == null || refs.isEmpty() || maxCount <= 0) {
            return List.of();
        }

        Map<String, ToolResultArtifactRef> uniqueByArtifactId = new LinkedHashMap<>();
        for (ToolResultArtifactRef ref : refs) {
            if (ref == null || !isValidArtifactId(ref.artifactId())) {
                continue;
            }
            ToolResultArtifactRef existing = uniqueByArtifactId.get(ref.artifactId());
            if (existing == null || ref.createdAtEpochMs() > existing.createdAtEpochMs()) {
                uniqueByArtifactId.put(ref.artifactId(), ref);
            }
        }
        if (uniqueByArtifactId.isEmpty()) {
            return List.of();
        }

        RehydrateSignals signals = extractSignals(uniqueByArtifactId.values(), modelMessages, currentUserMessage);
        if (signals.isEmpty()) {
            return List.of();
        }

        List<String> queryTerms = extractTerms(currentUserMessage == null ? null : currentUserMessage.content());
        Set<String> activeToolNames = extractVisibleToolNames(modelMessages);
        Set<String> alreadyRehydratedArtifactIds = extractRehydratedArtifactIds(modelMessages);
        List<ToolResultArtifactRef> candidates = uniqueByArtifactId.values().stream()
                .filter(ref -> ref != null
                        && !alreadyRehydratedArtifactIds.contains(ref.artifactId())
                        && (signals.artifactIds().contains(ref.artifactId())
                        || signals.toolNames().contains(lower(ref.toolName()))))
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<ToolResultArtifactRef> ranked = new ArrayList<>(candidates);
        ranked.sort((left, right) -> {
            int leftScore = scoreRef(left, queryTerms, activeToolNames, signals);
            int rightScore = scoreRef(right, queryTerms, activeToolNames, signals);
            if (leftScore != rightScore) {
                return Integer.compare(rightScore, leftScore);
            }
            return Long.compare(right.createdAtEpochMs(), left.createdAtEpochMs());
        });

        return ranked.subList(0, Math.min(maxCount, ranked.size()));
    }

    public static boolean isValidArtifactId(String artifactId) {
        if (artifactId == null || !artifactId.startsWith("sha256:")) {
            return false;
        }
        String hash = artifactId.substring("sha256:".length());
        if (hash.length() != 64) {
            return false;
        }
        for (int i = 0; i < hash.length(); i++) {
            char c = hash.charAt(i);
            boolean hex = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    private static int scoreRef(ToolResultArtifactRef ref,
                                List<String> queryTerms,
                                Set<String> activeToolNames,
                                RehydrateSignals signals) {
        if (ref == null) {
            return Integer.MIN_VALUE;
        }
        String toolName = lower(ref.toolName());
        String toolArguments = lower(ref.toolArguments());
        String corpus = toolName + " " + toolArguments;
        int score = 0;
        if (signals.artifactIds().contains(ref.artifactId())) {
            score += 10;
        }
        if (!toolName.isBlank() && signals.toolNames().contains(toolName)) {
            score += 6;
        }
        if (!toolName.isBlank() && activeToolNames.contains(toolName)) {
            score += 4;
        }
        for (String term : queryTerms) {
            if (!isMeaningfulQueryTerm(term)) {
                continue;
            }
            if (corpus.contains(term)) {
                score += 2;
            }
        }
        return score;
    }

    private static RehydrateSignals extractSignals(
            Iterable<ToolResultArtifactRef> refs,
            List<AiChatMessage> modelMessages,
            AiChatMessage currentUserMessage) {
        Set<String> refToolNames = new HashSet<>();
        for (ToolResultArtifactRef ref : refs) {
            if (ref == null) {
                continue;
            }
            String toolName = lower(ref.toolName());
            if (!toolName.isBlank()) {
                refToolNames.add(toolName);
            }
        }
        List<String> queryTerms = extractTerms(currentUserMessage == null ? null : currentUserMessage.content());
        Set<String> compressedCardArtifactIds = extractCompressedCardArtifactIds(modelMessages, queryTerms);
        Set<String> artifactIds = extractArtifactIds(currentUserMessage == null ? null : currentUserMessage.content());
        artifactIds = new HashSet<>(artifactIds);
        artifactIds.addAll(compressedCardArtifactIds);
        Set<String> toolNames = extractToolNameSignals(
                currentUserMessage == null ? null : currentUserMessage.content(),
                refToolNames
        );
        toolNames = new HashSet<>(toolNames);
        toolNames.retainAll(refToolNames);
        return new RehydrateSignals(artifactIds, toolNames);
    }

    private static Set<String> extractRehydratedArtifactIds(List<AiChatMessage> modelMessages) {
        if (modelMessages == null || modelMessages.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> artifactIds = new HashSet<>();
        for (AiChatMessage message : modelMessages) {
            if (message == null || message.role() != AiChatMessage.Role.TOOL) {
                continue;
            }
            String content = message.content();
            if (content == null || !content.startsWith("[Tool Result Rehydrated]")) {
                continue;
            }
            String artifactId = extractFieldValue(content, "artifactId=");
            if (isValidArtifactId(artifactId)) {
                artifactIds.add(artifactId);
            }
        }
        return artifactIds;
    }

    private static Set<String> extractArtifactIds(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> artifactIds = new HashSet<>();
        Matcher matcher = ARTIFACT_ID_PATTERN.matcher(text);
        while (matcher.find()) {
            artifactIds.add(matcher.group());
        }
        return artifactIds;
    }

    private static Set<String> extractToolNameSignals(String text, Set<String> validToolNames) {
        if (text == null || text.isBlank() || validToolNames == null || validToolNames.isEmpty()) {
            return Collections.emptySet();
        }
        String normalizedText = normalizeTokenSequence(text);
        if (normalizedText.isBlank()) {
            return Collections.emptySet();
        }
        String paddedText = " " + normalizedText + " ";
        Set<String> matched = new HashSet<>();
        for (String toolName : validToolNames) {
            String normalizedToolName = normalizeTokenSequence(toolName);
            if (normalizedToolName.isBlank()) {
                continue;
            }
            if (paddedText.contains(" " + normalizedToolName + " ")) {
                matched.add(lower(toolName));
            }
        }
        return matched;
    }

    private static Set<String> extractCompressedCardArtifactIds(
            List<AiChatMessage> modelMessages,
            List<String> queryTerms) {
        if (modelMessages == null || modelMessages.isEmpty() || queryTerms == null || queryTerms.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> artifactIds = new HashSet<>();
        for (AiChatMessage message : modelMessages) {
            if (message == null || message.role() != AiChatMessage.Role.TOOL) {
                continue;
            }
            String content = message.content();
            if (content == null || !content.startsWith("[Tool Result Context Compressed]")) {
                continue;
            }
            String summaryText = normalizeTokenSequence(extractCompressedSummary(content));
            if (summaryText.isBlank() || !containsAnyQueryTerm(summaryText, queryTerms)) {
                continue;
            }
            String artifactId = extractFieldValue(content, "artifactId=");
            if (isValidArtifactId(artifactId)) {
                artifactIds.add(artifactId);
            }
        }
        return artifactIds;
    }

    private static String extractCompressedSummary(String content) {
        String[] lines = content.split("\\R");
        StringBuilder summary = new StringBuilder();
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (!trimmed.startsWith("- ")) {
                continue;
            }
            String item = trimmed.substring(2).trim();
            if (item.isBlank() || "n/a".equalsIgnoreCase(item)) {
                continue;
            }
            if (!summary.isEmpty()) {
                summary.append(' ');
            }
            summary.append(item);
        }
        return summary.toString();
    }

    private static boolean containsAnyQueryTerm(String summaryText, List<String> queryTerms) {
        if (summaryText == null || summaryText.isBlank() || queryTerms == null || queryTerms.isEmpty()) {
            return false;
        }
        String paddedSummary = " " + summaryText + " ";
        for (String term : queryTerms) {
            if (!isMeaningfulQueryTerm(term)) {
                continue;
            }
            if (containsSummaryTerm(summaryText, paddedSummary, term)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsSummaryTerm(String summaryText, String paddedSummary, String term) {
        if (term == null || term.isBlank()) {
            return false;
        }
        if (term.codePoints().anyMatch(IndexedRehydrateSelector::isHanCodePoint)) {
            return summaryText.contains(term);
        }
        return paddedSummary.contains(" " + term + " ");
    }

    private static String extractFieldValue(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] lines = text.split("\\R");
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (!trimmed.startsWith(fieldName)) {
                continue;
            }
            return trimmed.substring(fieldName.length()).trim();
        }
        return "";
    }

    private static Set<String> extractVisibleToolNames(List<AiChatMessage> modelMessages) {
        if (modelMessages == null || modelMessages.isEmpty()) {
            return Set.of();
        }
        Set<String> toolNames = new HashSet<>();
        for (AiChatMessage message : modelMessages) {
            if (message == null) {
                continue;
            }
            if (message.role() == AiChatMessage.Role.TOOL) {
                String name = lower(message.name());
                if (!name.isBlank()) {
                    toolNames.add(name);
                }
                continue;
            }
            if (message.role() == AiChatMessage.Role.ASSISTANT && message.toolCalls() != null) {
                for (AiToolCall request : message.toolCalls()) {
                    if (request == null) {
                        continue;
                    }
                    String name = lower(request.name());
                    if (!name.isBlank()) {
                        toolNames.add(name);
                    }
                }
            }
        }
        return toolNames;
    }

    private static List<String> extractTerms(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = lower(text).replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsHan}]+", " ");
        if (normalized.isBlank()) {
            return List.of();
        }
        String[] parts = normalized.trim().split("\\s+");
        List<String> terms = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (!part.isBlank()) {
                terms.add(part);
            }
        }
        return terms;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String normalizeTokenSequence(String value) {
        String normalized = lower(value).replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsHan}]+", " ");
        return normalized.isBlank() ? "" : normalized.trim().replaceAll("\\s+", " ");
    }

    private static boolean isMeaningfulQueryTerm(String term) {
        if (term == null || term.isBlank()) {
            return false;
        }
        if (term.length() >= 2) {
            return true;
        }
        return term.codePoints().anyMatch(IndexedRehydrateSelector::isHanCodePoint);
    }

    private static boolean isHanCodePoint(int codePoint) {
        return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN;
    }

    private record RehydrateSignals(Set<String> artifactIds, Set<String> toolNames) {
        private static RehydrateSignals empty() {
            return new RehydrateSignals(Set.of(), Set.of());
        }

        private boolean isEmpty() {
            return artifactIds == null || artifactIds.isEmpty()
                    ? toolNames == null || toolNames.isEmpty()
                    : false;
        }
    }

}
