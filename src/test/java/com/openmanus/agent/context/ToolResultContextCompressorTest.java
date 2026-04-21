package com.openmanus.agent.context;

import com.openmanus.aiframework.runtime.model.AiChatMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolResultContextCompressorTest {

    @Test
    void shouldNotCompressToolResultWhenUnderThreshold() {
        ToolResultContextCompressor compressor = new ToolResultContextCompressor(512, 120, 80);
        AiChatMessage tool = new AiChatMessage(AiChatMessage.Role.TOOL, "short", "search", "call_1", List.of());

        List<AiChatMessage> result = compressor.compress(List.of(tool));

        assertEquals(1, result.size());
        assertSame(tool, result.getFirst());
    }

    @Test
    void shouldCompressOversizedToolResultAndKeepReferenceMetadata() {
        ToolResultContextCompressor compressor = new ToolResultContextCompressor(256, 80, 40);
        String artifactId = "sha256:" + "a".repeat(64);
        String large = """
                FACT: report ready
                ACTION: refreshed weather index
                TODO: rerun with latest station
                artifactId=%s
                %s
                """.formatted(artifactId, "X".repeat(420));
        AiChatMessage tool = new AiChatMessage(AiChatMessage.Role.TOOL, large, "browser", "call_9", List.of());

        List<AiChatMessage> result = compressor.compress(List.of(tool));
        String content = result.getFirst().content();

        assertTrue(content.startsWith("[Tool Result Context Compressed]"));
        assertTrue(content.contains("tool=browser"));
        assertTrue(content.contains("toolCallId=call_9"));
        assertTrue(content.contains("artifactId=" + artifactId));
        assertTrue(content.contains("originalChars="));
        assertTrue(content.contains("truncated=true"));
    }

    @Test
    void shouldKeepCompressedCardWithinConfiguredBudget() {
        ToolResultContextCompressor compressor = new ToolResultContextCompressor(256, 160, 160);
        String artifactId = "sha256:" + "b".repeat(64);
        String large = """
                FACT: this is a deliberately verbose summary line that should be shortened when the card itself exceeds budget
                ACTION: this action line is also intentionally long so the compressor must trim summary fields before returning
                TODO: keep only the minimum signal required for indexed rehydrate
                artifactId=%s
                %s
                """.formatted(artifactId, "K".repeat(1600));
        AiChatMessage tool = new AiChatMessage(AiChatMessage.Role.TOOL, large, "browser", "call_budget", List.of());

        List<AiChatMessage> result = compressor.compress(List.of(tool));
        String content = result.getFirst().content();

        assertTrue(content.startsWith("[Tool Result Context Compressed]"));
        assertTrue(content.contains("artifactId=" + artifactId));
        assertTrue(content.length() <= 256, "压缩卡片本身应受 maxChars 约束");
    }

    @Test
    void shouldProvideStructuredFallbackWhenNoTodoExists() {
        ToolResultContextCompressor compressor = new ToolResultContextCompressor(256, 80, 40);
        AiChatMessage tool = new AiChatMessage(
                AiChatMessage.Role.TOOL,
                "alpha line\nbeta line\n" + "Y".repeat(320),
                "shell",
                "call_2",
                List.of()
        );

        List<AiChatMessage> result = compressor.compress(List.of(tool));
        String content = result.getFirst().content();

        assertTrue(content.contains("keyFacts:\n- alpha line"));
        assertTrue(content.contains("recentActions:"));
        assertTrue(content.contains("todo:\n- n/a"));
        assertFalse(content.contains("artifactId=sha256:"), "无 artifactId 时应写入 n/a，而非伪造哈希");
        assertTrue(content.contains("artifactId=n/a"));
    }

    @Test
    void shouldKeepNullAndNonToolMessagesUnchanged() {
        ToolResultContextCompressor compressor = new ToolResultContextCompressor(256, 80, 40);
        AiChatMessage user = AiChatMessage.user("hello");
        List<AiChatMessage> input = new ArrayList<>();
        input.add(null);
        input.add(user);

        List<AiChatMessage> result = compressor.compress(input);

        assertEquals(2, result.size());
        assertSame(null, result.get(0));
        assertSame(user, result.get(1));
    }

    @Test
    void shouldNotRecompressAlreadyCompressedToolResult() {
        ToolResultContextCompressor compressor = new ToolResultContextCompressor(256, 80, 40);
        AiChatMessage tool = new AiChatMessage(
                AiChatMessage.Role.TOOL,
                "[Tool Result Context Compressed]\nartifactId=n/a\npreviewHead:\n" + "Z".repeat(400),
                "browser",
                "call_3",
                List.of()
        );

        List<AiChatMessage> result = compressor.compress(List.of(tool));

        assertEquals(1, result.size());
        assertSame(tool, result.getFirst());
    }
}
