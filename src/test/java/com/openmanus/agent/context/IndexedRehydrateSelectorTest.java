package com.openmanus.agent.context;

import com.openmanus.aiframework.runtime.ToolResultArtifactRef;
import com.openmanus.aiframework.runtime.model.AiChatMessage;
import com.openmanus.aiframework.runtime.model.AiToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndexedRehydrateSelectorTest {

    @Test
    void shouldSelectHigherScoreThenLatestArtifact() {
        String relevantId = "sha256:" + "a".repeat(64);
        String tieOlderId = "sha256:" + "b".repeat(64);
        String tieLatestId = "sha256:" + "c".repeat(64);
        List<ToolResultArtifactRef> refs = List.of(
                new ToolResultArtifactRef("sha256:bad", "browser", "weather", 10, 300L),
                new ToolResultArtifactRef(tieOlderId, "browser", "report", 10, 100L),
                new ToolResultArtifactRef(tieLatestId, "browser", "report", 10, 200L),
                new ToolResultArtifactRef(relevantId, "browser", "weather report", 10, 50L)
        );
        List<AiChatMessage> modelMessages = List.of(
                AiChatMessage.assistant("tool", List.of(new AiToolCall("call_1", "browser", "{}")))
        );
        AiChatMessage currentUserMessage = AiChatMessage.user("browser weather report");

        List<ToolResultArtifactRef> selected = IndexedRehydrateSelector.select(
                refs,
                modelMessages,
                currentUserMessage,
                2
        );

        assertEquals(2, selected.size());
        assertEquals(relevantId, selected.get(0).artifactId(), "相关度更高项应排在第一");
        assertEquals(tieLatestId, selected.get(1).artifactId(), "同分时应选择更晚创建项");
    }

    @Test
    void shouldReturnEmptyWhenNoExplicitSignal() {
        String id = "sha256:" + "a".repeat(64);
        List<ToolResultArtifactRef> refs = List.of(
                new ToolResultArtifactRef(id, "browser", "weather report", 10, 100L)
        );
        AiChatMessage currentUserMessage = AiChatMessage.user("just say hi");

        List<ToolResultArtifactRef> selected = IndexedRehydrateSelector.select(
                refs,
                List.of(),
                currentUserMessage,
                1
        );

        assertEquals(0, selected.size(), "没有 artifactId 或 tool 关联信号时不应回填");
    }

    @Test
    void shouldSelectByExplicitArtifactIdWhenRefToolNameIsMissing() {
        String explicitId = "sha256:" + "d".repeat(64);
        List<ToolResultArtifactRef> refs = List.of(
                new ToolResultArtifactRef(explicitId, "", "{}", 10, 100L)
        );
        AiChatMessage currentUserMessage = AiChatMessage.user("please rehydrate " + explicitId);

        List<ToolResultArtifactRef> selected = IndexedRehydrateSelector.select(
                refs,
                List.of(),
                currentUserMessage,
                1
        );

        assertEquals(1, selected.size(), "toolName 为空时，显式 artifactId 仍应触发回填");
        assertEquals(explicitId, selected.getFirst().artifactId());
    }

    @Test
    void shouldSelectByExplicitArtifactIdWrappedBySentencePunctuation() {
        String explicitId = "sha256:" + "1".repeat(64);
        List<ToolResultArtifactRef> refs = List.of(
                new ToolResultArtifactRef(explicitId, "", "{}", 10, 100L)
        );
        AiChatMessage currentUserMessage = AiChatMessage.user("please rehydrate artifact " + explicitId + ".");

        List<ToolResultArtifactRef> selected = IndexedRehydrateSelector.select(
                refs,
                List.of(),
                currentUserMessage,
                1
        );

        assertEquals(1, selected.size(), "句尾英文标点不应导致 artifactId 信号丢失");
        assertEquals(explicitId, selected.getFirst().artifactId());
    }

    @Test
    void shouldSelectByExplicitArtifactIdWrappedByChinesePunctuation() {
        String explicitId = "sha256:" + "2".repeat(64);
        List<ToolResultArtifactRef> refs = List.of(
                new ToolResultArtifactRef(explicitId, "", "{}", 10, 100L)
        );
        AiChatMessage currentUserMessage = AiChatMessage.user("请回填「" + explicitId + "」。");

        List<ToolResultArtifactRef> selected = IndexedRehydrateSelector.select(
                refs,
                List.of(),
                currentUserMessage,
                1
        );

        assertEquals(1, selected.size(), "中文引号和句号包裹时仍应识别 artifactId");
        assertEquals(explicitId, selected.getFirst().artifactId());
    }

    @Test
    void shouldSkipAlreadyRehydratedArtifactInModelMessages() {
        String artifactId = "sha256:" + "e".repeat(64);
        List<ToolResultArtifactRef> refs = List.of(
                new ToolResultArtifactRef(artifactId, "browser", "{}", 10, 100L)
        );
        List<AiChatMessage> modelMessages = List.of(
                new AiChatMessage(
                        AiChatMessage.Role.TOOL,
                        """
                                [Tool Result Rehydrated]
                                source=index
                                tool=browser
                                artifactId=%s
                                originalChars=123
                                text:
                                cached payload
                                """.formatted(artifactId),
                        "browser",
                        "call_1",
                        List.of()
                )
        );
        AiChatMessage currentUserMessage = AiChatMessage.user("please rehydrate " + artifactId);

        List<ToolResultArtifactRef> selected = IndexedRehydrateSelector.select(
                refs,
                modelMessages,
                currentUserMessage,
                1
        );

        assertEquals(0, selected.size(), "同一 artifact 已回填时不应重复注入");
    }

    @Test
    void shouldNotMatchToolSignalBySubstringConflict() {
        String artifactId = "sha256:" + "f".repeat(64);
        List<ToolResultArtifactRef> refs = List.of(
                new ToolResultArtifactRef(artifactId, "file", "{}", 10, 100L)
        );
        AiChatMessage currentUserMessage = AiChatMessage.user("please check my profile settings");

        List<ToolResultArtifactRef> selected = IndexedRehydrateSelector.select(
                refs,
                List.of(),
                currentUserMessage,
                1
        );

        assertEquals(0, selected.size(), "profile 不应误命中 file 工具信号");
    }

    @Test
    void shouldMatchToolSignalByTokenBoundary() {
        String artifactId = "sha256:" + "0".repeat(64);
        List<ToolResultArtifactRef> refs = List.of(
                new ToolResultArtifactRef(artifactId, "file", "{}", 10, 100L)
        );
        AiChatMessage currentUserMessage = AiChatMessage.user("please use file tool to read config");

        List<ToolResultArtifactRef> selected = IndexedRehydrateSelector.select(
                refs,
                List.of(),
                currentUserMessage,
                1
        );

        assertEquals(1, selected.size(), "显式提及 tool 名应命中回填");
        assertEquals(artifactId, selected.getFirst().artifactId());
    }

    @Test
    void shouldSelectArtifactWhenCurrentQuestionMatchesCompressedCardSummary() {
        String artifactId = "sha256:" + "3".repeat(64);
        List<ToolResultArtifactRef> refs = List.of(
                new ToolResultArtifactRef(artifactId, "browser", "{}", 1200, 100L)
        );
        List<AiChatMessage> modelMessages = List.of(
                new AiChatMessage(
                        AiChatMessage.Role.TOOL,
                        """
                                [Tool Result Context Compressed]
                                source=context-budget
                                tool=browser
                                toolCallId=call_1
                                artifactId=%s
                                originalChars=4096
                                maxChars=1800
                                truncated=true
                                keyFacts:
                                - weather station cache ready
                                recentActions:
                                - refreshed seattle report
                                todo:
                                - compare hourly trend before final answer
                                previewHead:
                                ...
                                previewTail:
                                ...
                                """.formatted(artifactId),
                        "browser",
                        "call_1",
                        List.of()
                )
        );
        AiChatMessage currentUserMessage = AiChatMessage.user("please compare hourly trend before final answer");

        List<ToolResultArtifactRef> selected = IndexedRehydrateSelector.select(
                refs,
                modelMessages,
                currentUserMessage,
                1
        );

        assertEquals(1, selected.size(), "命中压缩卡片摘要时应触发对应 artifact 回填");
        assertEquals(artifactId, selected.getFirst().artifactId());
    }

    @Test
    void shouldSelectArtifactWhenChineseSingleCharacterQueryMatchesCompressedSummary() {
        String artifactId = "sha256:" + "7".repeat(64);
        List<ToolResultArtifactRef> refs = List.of(
                new ToolResultArtifactRef(artifactId, "browser", "{\"q\":\"上海天气\"}", 1200, 100L)
        );
        List<AiChatMessage> modelMessages = List.of(
                new AiChatMessage(
                        AiChatMessage.Role.TOOL,
                        """
                                [Tool Result Context Compressed]
                                source=context-budget
                                tool=browser
                                toolCallId=call_1
                                artifactId=%s
                                originalChars=4096
                                maxChars=1800
                                truncated=true
                                keyFacts:
                                - 雨
                                recentActions:
                                - 上海天气已刷新
                                todo:
                                - 比较逐小时趋势
                                previewHead:
                                ...
                                previewTail:
                                ...
                                """.formatted(artifactId),
                        "browser",
                        "call_1",
                        List.of()
                )
        );
        AiChatMessage currentUserMessage = AiChatMessage.user("雨");

        List<ToolResultArtifactRef> selected = IndexedRehydrateSelector.select(
                refs,
                modelMessages,
                currentUserMessage,
                1
        );

        assertEquals(1, selected.size(), "中文单字摘要命中时也应触发对应 artifact 回填");
        assertEquals(artifactId, selected.getFirst().artifactId());
    }

    @Test
    void shouldNotMatchCompressedSummaryByEnglishSubstringConflict() {
        String artifactId = "sha256:" + "8".repeat(64);
        List<ToolResultArtifactRef> refs = List.of(
                new ToolResultArtifactRef(artifactId, "browser", "{\"q\":\"status\"}", 1200, 100L)
        );
        List<AiChatMessage> modelMessages = List.of(
                new AiChatMessage(
                        AiChatMessage.Role.TOOL,
                        """
                                [Tool Result Context Compressed]
                                source=context-budget
                                tool=browser
                                toolCallId=call_1
                                artifactId=%s
                                originalChars=4096
                                maxChars=1800
                                truncated=true
                                keyFacts:
                                - report ready
                                recentActions:
                                - refreshed status board
                                todo:
                                - compare report before final answer
                                previewHead:
                                ...
                                previewTail:
                                ...
                                """.formatted(artifactId),
                        "browser",
                        "call_1",
                        List.of()
                )
        );
        AiChatMessage currentUserMessage = AiChatMessage.user("port");

        List<ToolResultArtifactRef> selected = IndexedRehydrateSelector.select(
                refs,
                modelMessages,
                currentUserMessage,
                1
        );

        assertEquals(0, selected.size(), "英文子串命中不应触发压缩摘要回填");
    }

    @Test
    void shouldNotWidenCompressedCardSummarySignalToOtherArtifactsOfSameTool() {
        String matchedArtifactId = "sha256:" + "5".repeat(64);
        String unrelatedArtifactId = "sha256:" + "6".repeat(64);
        List<ToolResultArtifactRef> refs = List.of(
                new ToolResultArtifactRef(unrelatedArtifactId, "browser", "{\"city\":\"boston\"}", 1200, 200L),
                new ToolResultArtifactRef(matchedArtifactId, "browser", "{\"city\":\"seattle\"}", 1200, 100L)
        );
        List<AiChatMessage> modelMessages = List.of(
                new AiChatMessage(
                        AiChatMessage.Role.TOOL,
                        """
                                [Tool Result Context Compressed]
                                source=context-budget
                                tool=browser
                                toolCallId=call_1
                                artifactId=%s
                                originalChars=4096
                                maxChars=1800
                                truncated=true
                                keyFacts:
                                - weather station cache ready
                                recentActions:
                                - refreshed seattle report
                                todo:
                                - compare hourly trend before final answer
                                previewHead:
                                ...
                                previewTail:
                                ...
                                """.formatted(matchedArtifactId),
                        "browser",
                        "call_1",
                        List.of()
                )
        );
        AiChatMessage currentUserMessage = AiChatMessage.user("please compare hourly trend before final answer");

        List<ToolResultArtifactRef> selected = IndexedRehydrateSelector.select(
                refs,
                modelMessages,
                currentUserMessage,
                2
        );

        assertEquals(1, selected.size(), "压缩卡片摘要命中后不应扩散到同工具的其他 artifact");
        assertEquals(matchedArtifactId, selected.getFirst().artifactId());
    }

    @Test
    void shouldNotTreatCompressedCardToolNameAsImplicitRehydrateSignal() {
        String artifactId = "sha256:" + "4".repeat(64);
        List<ToolResultArtifactRef> refs = List.of(
                new ToolResultArtifactRef(artifactId, "browser", "{}", 1200, 100L)
        );
        List<AiChatMessage> modelMessages = List.of(
                new AiChatMessage(
                        AiChatMessage.Role.TOOL,
                        """
                                [Tool Result Context Compressed]
                                source=context-budget
                                tool=browser
                                toolCallId=call_1
                                artifactId=%s
                                originalChars=4096
                                maxChars=1800
                                truncated=true
                                keyFacts:
                                - weather station cache ready
                                recentActions:
                                - refreshed seattle report
                                todo:
                                - compare hourly trend before final answer
                                previewHead:
                                ...
                                previewTail:
                                ...
                                """.formatted(artifactId),
                        "browser",
                        "call_1",
                        List.of()
                )
        );
        AiChatMessage currentUserMessage = AiChatMessage.user("just say hi");

        List<ToolResultArtifactRef> selected = IndexedRehydrateSelector.select(
                refs,
                modelMessages,
                currentUserMessage,
                1
        );

        assertEquals(0, selected.size(), "压缩卡片存在本身不应成为隐式回填信号");
    }
}
