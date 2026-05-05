package com.openmanus.agent.context;

import com.openmanus.aiframework.runtime.AiSessionSandboxGateway;
import com.openmanus.aiframework.runtime.model.AiChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Single pre-model API budget gate for tool results.
 */
@Slf4j
public final class ToolResultBudget {

    private static final String DEFAULT_USER_ID = "001";
    private static final DateTimeFormatter FILE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS");

    private final AiSessionSandboxGateway sandboxGateway;
    private final boolean enabled;
    private final int minChars;
    private final int previewHeadChars;
    private final int previewTailChars;
    private final int decayChars;
    private final Clock clock;

    public ToolResultBudget(AiSessionSandboxGateway sandboxGateway,
                            boolean enabled,
                            int minChars,
                            int previewHeadChars,
                            int previewTailChars,
                            int decayChars) {
        this(sandboxGateway, enabled, minChars, previewHeadChars, previewTailChars, decayChars, Clock.systemDefaultZone());
    }

    ToolResultBudget(AiSessionSandboxGateway sandboxGateway,
                     boolean enabled,
                     int minChars,
                     int previewHeadChars,
                     int previewTailChars,
                     int decayChars,
                     Clock clock) {
        this.sandboxGateway = sandboxGateway;
        this.enabled = enabled;
        this.minChars = Math.max(256, minChars);
        this.previewHeadChars = Math.max(64, previewHeadChars);
        this.previewTailChars = Math.max(32, previewTailChars);
        this.decayChars = Math.max(0, decayChars);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public AiChatMessage budget(AiChatMessage message) {
        if (!enabled || sandboxGateway == null || message == null || message.role() != AiChatMessage.Role.TOOL) {
            return message;
        }
        String content = message.content();
        if (content == null || content.length() < effectiveMinChars()) {
            return message;
        }
        if (content.startsWith("[Tool Result Stub]")) {
            return message;
        }
        try {
            String sha256 = sha256Hex(content);
            String shortHash = sha256.substring(0, Math.min(12, sha256.length()));
            String fileName = FILE_TIME_FORMAT.format(LocalDateTime.now(clock)) + "-" + shortHash + ".txt";
            String path = ".openmanus/tool-results/" + fileName;
            String sandboxKey = currentUserId();
            String resolvedPath = sandboxGateway.resolveWorkspacePath(sandboxKey, path);
            sandboxGateway.writeTextFile(sandboxKey, resolvedPath, content);
            String stub = buildStub(message.name(), path, content, sha256);
            log.info("tool-result-budget offloaded tool={} chars={} path={}",
                    message.name(), content.length(), resolvedPath);
            return new AiChatMessage(
                    message.role(),
                    stub,
                    message.name(),
                    message.toolCallId(),
                    List.of()
            );
        } catch (RuntimeException e) {
            log.warn("tool-result-budget failed, keeping inline tool result: tool={} chars={}",
                    message.name(), content.length(), e);
            return message;
        }
    }

    private int effectiveMinChars() {
        if (decayChars <= 0) {
            return minChars;
        }
        return Math.min(minChars, decayChars);
    }

    private String buildStub(String toolName, String path, String content, String sha256) {
        int head = Math.min(previewHeadChars, content.length());
        int tail = Math.min(previewTailChars, Math.max(0, content.length() - head));
        String headPart = content.substring(0, head);
        String tailPart = tail > 0 ? content.substring(content.length() - tail) : "";
        return """
                [Tool Result Stub]
                tool=%s
                path=%s
                originalChars=%d
                sha256=%s
                truncated=true
                readHint=Use runShellCommand with cat/head/tail/grep/rg to inspect the saved file. Use explicit paths inside the workspace.
                previewHead:
                %s
                previewTail:
                %s
                """.formatted(toolName == null ? "" : toolName, path, content.length(), sha256, headPart, tailPart);
    }

    private static String currentUserId() {
        String userId = MDC.get("userId");
        if (userId == null || userId.isBlank()) {
            return DEFAULT_USER_ID;
        }
        return userId;
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
