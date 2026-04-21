package com.openmanus.agent.context;

import com.openmanus.aiframework.runtime.model.AiChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Compresses oversized tool-observation messages before model input assembly.
 */
public final class ToolResultContextCompressor {

    private static final int DEFAULT_MAX_CHARS = 1800;
    private static final int DEFAULT_HEAD_CHARS = 360;
    private static final int DEFAULT_TAIL_CHARS = 240;
    private static final int MIN_MAX_CHARS = 256;
    private static final int MIN_HEAD_CHARS = 64;
    private static final int MIN_TAIL_CHARS = 32;

    private final int maxChars;
    private final int headChars;
    private final int tailChars;

    public ToolResultContextCompressor() {
        this(DEFAULT_MAX_CHARS, DEFAULT_HEAD_CHARS, DEFAULT_TAIL_CHARS);
    }

    public ToolResultContextCompressor(int maxChars, int headChars, int tailChars) {
        this.maxChars = Math.max(MIN_MAX_CHARS, maxChars);
        this.headChars = Math.max(MIN_HEAD_CHARS, headChars);
        this.tailChars = Math.max(MIN_TAIL_CHARS, tailChars);
    }

    public List<AiChatMessage> compress(List<AiChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<AiChatMessage> result = new ArrayList<>(messages.size());
        for (AiChatMessage message : messages) {
            result.add(compress(message));
        }
        return result;
    }

    private AiChatMessage compress(AiChatMessage message) {
        if (message == null || message.role() != AiChatMessage.Role.TOOL) {
            return message;
        }
        String content = message.content();
        if (content == null || content.length() <= maxChars || isAlreadyCompacted(content)) {
            return message;
        }

        int head = Math.min(headChars, content.length());
        int tail = Math.min(tailChars, Math.max(0, content.length() - head));
        String headPart = content.substring(0, head);
        String tailPart = tail > 0 ? content.substring(content.length() - tail) : "";

        String keyFact = firstNonBlankLine(content);
        String recentAction = lastNonBlankLine(content);
        String todo = firstTodoLine(content);
        String artifactId = extractArtifactId(content);

        String compressed = formatCompressedCard(
                message,
                artifactId,
                content.length(),
                keyFact,
                recentAction,
                todo,
                headPart,
                tailPart
        );
        if (compressed.length() > maxChars) {
            String[] fitted = fitSegmentsWithinBudget(keyFact, recentAction, todo, headPart, tailPart, artifactId, message, content.length());
            compressed = formatCompressedCard(
                    message,
                    artifactId,
                    content.length(),
                    fitted[0],
                    fitted[1],
                    fitted[2],
                    fitted[3],
                    fitted[4]
            );
        }
        if (compressed.length() > maxChars) {
            compressed = hardLimit(compressed, maxChars);
        }

        return new AiChatMessage(
                AiChatMessage.Role.TOOL,
                compressed,
                message.name(),
                message.toolCallId(),
                message.toolCalls()
        );
    }

    private String formatCompressedCard(AiChatMessage message,
                                        String artifactId,
                                        int originalChars,
                                        String keyFact,
                                        String recentAction,
                                        String todo,
                                        String headPart,
                                        String tailPart) {
        return """
                [Tool Result Context Compressed]
                source=context-budget
                tool=%s
                toolCallId=%s
                artifactId=%s
                originalChars=%d
                maxChars=%d
                truncated=true
                keyFacts:
                - %s
                recentActions:
                - %s
                todo:
                - %s
                previewHead:
                %s
                previewTail:
                %s
                """
                .formatted(
                        normalize(message.name(), "unknown_tool"),
                        normalize(message.toolCallId(), "n/a"),
                        normalize(artifactId, "n/a"),
                        originalChars,
                        maxChars,
                        keyFact,
                        recentAction,
                        todo,
                        headPart,
                        tailPart
                );
    }

    private String[] fitSegmentsWithinBudget(String keyFact,
                                             String recentAction,
                                             String todo,
                                             String headPart,
                                             String tailPart,
                                             String artifactId,
                                             AiChatMessage message,
                                             int originalChars) {
        String fittedKeyFact = keyFact;
        String fittedRecentAction = recentAction;
        String fittedTodo = todo;
        String fittedHeadPart = headPart;
        String fittedTailPart = tailPart;
        for (int i = 0; i < 16; i++) {
            String candidate = formatCompressedCard(
                    message,
                    artifactId,
                    originalChars,
                    fittedKeyFact,
                    fittedRecentAction,
                    fittedTodo,
                    fittedHeadPart,
                    fittedTailPart
            );
            int overflow = candidate.length() - maxChars;
            if (overflow <= 0) {
                break;
            }
            int headOverflow = shrinkAmount(fittedHeadPart, overflow);
            if (headOverflow > 0) {
                fittedHeadPart = fittedHeadPart.substring(0, fittedHeadPart.length() - headOverflow);
                continue;
            }
            int tailOverflow = shrinkAmount(fittedTailPart, overflow);
            if (tailOverflow > 0) {
                fittedTailPart = fittedTailPart.substring(tailOverflow);
                continue;
            }
            int recentOverflow = shrinkAmount(fittedRecentAction, overflow);
            if (recentOverflow > 0) {
                fittedRecentAction = trimWithEllipsis(fittedRecentAction, fittedRecentAction.length() - recentOverflow);
                continue;
            }
            int keyOverflow = shrinkAmount(fittedKeyFact, overflow);
            if (keyOverflow > 0) {
                fittedKeyFact = trimWithEllipsis(fittedKeyFact, fittedKeyFact.length() - keyOverflow);
                continue;
            }
            int todoOverflow = shrinkAmount(fittedTodo, overflow);
            if (todoOverflow > 0) {
                fittedTodo = trimWithEllipsis(fittedTodo, fittedTodo.length() - todoOverflow);
                continue;
            }
            break;
        }
        return new String[]{fittedKeyFact, fittedRecentAction, fittedTodo, fittedHeadPart, fittedTailPart};
    }

    private static int shrinkAmount(String value, int overflow) {
        if (value == null || value.isEmpty() || overflow <= 0) {
            return 0;
        }
        return Math.min(value.length(), Math.max(1, overflow));
    }

    private static String trimWithEllipsis(String value, int targetLength) {
        if (value == null || value.isBlank()) {
            return "n/a";
        }
        if (targetLength <= 0) {
            return "n/a";
        }
        if (value.length() <= targetLength) {
            return value;
        }
        if (targetLength <= 3) {
            return value.substring(0, targetLength);
        }
        return value.substring(0, targetLength - 3) + "...";
    }

    private static String hardLimit(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        if (maxChars <= 3) {
            return value.substring(0, maxChars);
        }
        return value.substring(0, maxChars - 3) + "...";
    }

    private static boolean isAlreadyCompacted(String content) {
        String lower = content.toLowerCase(Locale.ROOT);
        return lower.startsWith("[tool result compacted]")
                || lower.startsWith("[tool result offloaded]")
                || lower.startsWith("[tool result context compressed]");
    }

    private static String extractArtifactId(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] lines = text.split("\\R");
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (!trimmed.startsWith("artifactId=")) {
                continue;
            }
            String artifactId = trimmed.substring("artifactId=".length()).trim();
            if (IndexedRehydrateSelector.isValidArtifactId(artifactId)) {
                return artifactId;
            }
        }
        return null;
    }

    private static String firstNonBlankLine(String text) {
        String[] lines = text == null ? new String[0] : text.split("\\R");
        for (String line : lines) {
            String trimmed = sanitizeLine(line);
            if (!trimmed.isBlank()) {
                return trimmed;
            }
        }
        return "n/a";
    }

    private static String lastNonBlankLine(String text) {
        String[] lines = text == null ? new String[0] : text.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String trimmed = sanitizeLine(lines[i]);
            if (!trimmed.isBlank()) {
                return trimmed;
            }
        }
        return "n/a";
    }

    private static String firstTodoLine(String text) {
        String[] lines = text == null ? new String[0] : text.split("\\R");
        for (String line : lines) {
            String lowered = line == null ? "" : line.toLowerCase(Locale.ROOT);
            if (lowered.contains("todo")
                    || lowered.contains("next")
                    || lowered.contains("待办")
                    || lowered.contains("下一步")) {
                String candidate = sanitizeLine(line);
                if (!candidate.isBlank()) {
                    return candidate;
                }
            }
        }
        return "n/a";
    }

    private static String sanitizeLine(String line) {
        if (line == null) {
            return "";
        }
        String compact = line.strip().replaceAll("\\s+", " ");
        if (compact.length() <= 120) {
            return compact;
        }
        return compact.substring(0, 117) + "...";
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
