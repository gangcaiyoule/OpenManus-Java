package com.openmanus.agent.base;

import com.openmanus.aiframework.runtime.AiChatModel;
import com.openmanus.aiframework.runtime.AiMemory;
import com.openmanus.aiframework.runtime.AiMemoryProvider;
import com.openmanus.aiframework.runtime.AiToolResultArtifactStore;
import com.openmanus.aiframework.runtime.ToolResultArtifactRef;
import com.openmanus.aiframework.runtime.model.AiChatMessage;
import com.openmanus.aiframework.runtime.model.AiChatRequest;
import com.openmanus.aiframework.runtime.model.AiChatResponse;
import com.openmanus.aiframework.runtime.model.AiToolCall;
import com.openmanus.aiframework.tool.AiParam;
import com.openmanus.aiframework.tool.AiTool;
import com.openmanus.infra.memory.FileChatMemoryStore;
import com.openmanus.infra.memory.InMemoryAiMemoryStore;
import com.openmanus.infra.memory.PersistentAiMemory;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractAgentExecutorChatMemoryIntegrationTest {

    @Test
    void shouldPersistToolResultIntoRuntimeMemoryAndReuseInNextTurn() {
        RecordingScriptedRuntimeModel runtimeModel = new RecordingScriptedRuntimeModel(List.of(
                assistantToolCall("call_1", "echo", "{\"text\":\"hello\"}"),
                assistant("first-done"),
                assistant("second-done")
        ));
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        AiMemoryProvider memoryProvider = memoryId -> new PersistentAiMemory(memoryId, store);

        TestAgent agent = TestAgent.builder()
                .aiChatModel(runtimeModel)
                .aiMemoryProvider(memoryProvider)
                .toolFromObject(new EchoTool())
                .build();

        String memoryId = "conv-tool-memory";
        String firstResult = agent.execute("请调用工具", memoryId);
        String secondResult = agent.execute("你还记得上轮工具输出吗", memoryId);

        assertEquals("first-done", firstResult);
        assertEquals("second-done", secondResult);

        AiMemory memory = memoryProvider.get(memoryId);
        boolean hasToolResultMessage = memory.messages().stream()
                .filter(message -> message.role() == AiChatMessage.Role.TOOL)
                .anyMatch(message -> "echo".equals(message.name())
                        && message.content().contains("echo-result:hello"));
        assertTrue(hasToolResultMessage, "工具结果应写入 Runtime Memory");

        List<AiChatMessage> thirdModelCall = runtimeModel.requests().get(2).messages();
        boolean thirdCallContainsPreviousToolResult = thirdModelCall.stream()
                .filter(message -> message.role() == AiChatMessage.Role.TOOL)
                .anyMatch(message -> message.content().contains("echo-result:hello"));
        assertTrue(thirdCallContainsPreviousToolResult, "下一轮请求应携带上一轮工具结果上下文");
    }

    @Test
    void shouldCompactLargeToolResultBeforePersistingIntoMemory() {
        RecordingScriptedRuntimeModel runtimeModel = new RecordingScriptedRuntimeModel(List.of(
                assistantToolCall("call_1", "dumpLarge", "{}"),
                assistant("done")
        ));
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        AiMemoryProvider memoryProvider = memoryId -> new PersistentAiMemory(memoryId, store);

        TestAgent agent = TestAgent.builder()
                .aiChatModel(runtimeModel)
                .aiMemoryProvider(memoryProvider)
                .enableToolResultCompaction(true)
                .memoryToolResultMaxChars(256)
                .compactToolResultHeadChars(80)
                .compactToolResultTailChars(40)
                .toolFromObject(new LargeOutputTool())
                .build();

        String memoryId = "conv-compact-tool-result";
        String result = agent.execute("请生成大结果", memoryId);
        assertEquals("done", result);

        String persistedToolResult = memoryProvider.get(memoryId).messages().stream()
                .filter(message -> message.role() == AiChatMessage.Role.TOOL)
                .map(AiChatMessage::content)
                .findFirst()
                .orElse("");
        assertTrue(persistedToolResult.contains("[Tool Result Compacted]"), "超长工具结果应压缩写入 memory");
        assertTrue(persistedToolResult.contains("sha256="), "压缩结果应保留可追溯摘要信息");

        List<AiChatMessage> secondModelCall = runtimeModel.requests().get(1).messages();
        boolean secondRoundUsesCompactedPayload = secondModelCall.stream()
                .filter(message -> message.role() == AiChatMessage.Role.TOOL)
                .anyMatch(message -> message.content().contains("[Tool Result Compacted]"));
        assertTrue(secondRoundUsesCompactedPayload, "同一 execute 的下一轮也应使用压缩结果");
    }

    @Test
    void shouldOffloadLargeToolResultLosslesslyWhenEnabled() {
        RecordingScriptedRuntimeModel runtimeModel = new RecordingScriptedRuntimeModel(List.of(
                assistantToolCall("call_1", "dumpLarge", "{}"),
                assistant("done")
        ));
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        AiMemoryProvider memoryProvider = memoryId -> new PersistentAiMemory(memoryId, store);
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();

        TestAgent agent = TestAgent.builder()
                .aiChatModel(runtimeModel)
                .aiMemoryProvider(memoryProvider)
                .enableToolResultOffload(true)
                .toolResultOffloadMinChars(256)
                .toolResultArtifactStore(artifactStore)
                .toolFromObject(new LargeOutputTool())
                .build();

        String memoryId = "conv-offload-tool-result";
        String result = agent.execute("请卸载大结果", memoryId);
        assertEquals("done", result);

        String persistedToolResult = memoryProvider.get(memoryId).messages().stream()
                .filter(message -> message.role() == AiChatMessage.Role.TOOL)
                .map(AiChatMessage::content)
                .findFirst()
                .orElse("");
        assertTrue(persistedToolResult.contains("[Tool Result Offloaded]"), "开启卸载后应写入 offload 索引卡片");
        assertTrue(persistedToolResult.contains("artifactId=sha256:"), "offload 卡片应包含 artifactId");
        assertEquals(1, artifactStore.size(), "应保存一份无损 artifact");
        assertTrue(artifactStore.firstPayload().length() >= 3000, "artifact 应保留完整工具输出");
    }

    @Test
    void shouldRehydrateOffloadedToolResultIntoModelInputWhenEnabled() {
        RecordingScriptedRuntimeModel runtimeModel = new RecordingScriptedRuntimeModel(List.of(
                assistantToolCall("call_1", "dumpLarge", "{}"),
                assistant("first-done"),
                assistant("second-done")
        ));
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        AiMemoryProvider memoryProvider = memoryId -> new PersistentAiMemory(memoryId, store);
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();

        TestAgent agent = TestAgent.builder()
                .aiChatModel(runtimeModel)
                .aiMemoryProvider(memoryProvider)
                .enableToolResultOffload(true)
                .toolResultOffloadMinChars(256)
                .enableToolResultRehydrate(true)
                .toolResultRehydrateMaxChars(4000)
                .toolResultArtifactStore(artifactStore)
                .toolFromObject(new LargeOutputTool())
                .build();

        String memoryId = "conv-offload-rehydrate";
        assertEquals("first-done", agent.execute("first-question", memoryId));
        String artifactId = memoryProvider.get(memoryId).messages().stream()
                .filter(message -> message.role() == AiChatMessage.Role.TOOL)
                .map(AiChatMessage::content)
                .map(AbstractAgentExecutorChatMemoryIntegrationTest::extractArtifactId)
                .filter(id -> id != null && !id.isBlank())
                .findFirst()
                .orElseThrow();
        assertEquals("second-done", agent.execute("please rehydrate " + artifactId, memoryId));

        List<AiChatMessage> thirdCall = runtimeModel.requests().get(2).messages();
        String payload = thirdCall.toString();
        assertTrue(payload.contains("[Tool Result Rehydrated]"), "回填开启后应向模型输入注入回填内容");
        assertTrue(payload.contains("artifactId=sha256:"), "回填块应保留 artifactId");
    }

    @Test
    void shouldIgnoreMalformedArtifactIdDuringIndexedRehydrationSelection() {
        RecordingScriptedRuntimeModel runtimeModel = new RecordingScriptedRuntimeModel(List.of(
                assistant("done")
        ));
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        AiMemoryProvider memoryProvider = memoryId -> new PersistentAiMemory(memoryId, store);
        MalformedIdTrackingArtifactStore artifactStore = new MalformedIdTrackingArtifactStore();

        TestAgent agent = TestAgent.builder()
                .aiChatModel(runtimeModel)
                .aiMemoryProvider(memoryProvider)
                .enableToolResultRehydrate(true)
                .toolResultRehydrateMaxChars(4000)
                .toolResultArtifactStore(artifactStore)
                .build();

        assertEquals("done", agent.execute("question", "conv-malformed-artifact-id"));
        assertEquals(0, artifactStore.loadCalls(), "非法 artifactId 应在选择阶段被过滤，不能触发存储加载");

        String payload = runtimeModel.requests().getFirst().messages().toString();
        assertFalse(payload.contains("[Tool Result Rehydrated]"), "非法 artifactId 不能注入回填消息");
    }

    @Test
    void shouldPreferHigherRankedArtifactsWhenIndexedRehydrationIsLimited() {
        RecordingScriptedRuntimeModel runtimeModel = new RecordingScriptedRuntimeModel(List.of(
                assistant("done")
        ));
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        AiMemoryProvider memoryProvider = memoryId -> new PersistentAiMemory(memoryId, store);
        RankedArtifactStore artifactStore = new RankedArtifactStore();

        TestAgent agent = TestAgent.builder()
                .aiChatModel(runtimeModel)
                .aiMemoryProvider(memoryProvider)
                .enableToolResultRehydrate(true)
                .toolResultRehydrateMaxChars(4000)
                .toolResultRehydrateMaxPerRound(1)
                .toolResultArtifactStore(artifactStore)
                .build();

        assertEquals("done", agent.execute("browser weather report", "conv-ranked-artifact"));

        String payload = runtimeModel.requests().getFirst().messages().toString();
        assertTrue(payload.contains("RELEVANT_PAYLOAD"), "应优先注入相关度最高的 artifact");
        assertFalse(payload.contains("IRRELEVANT_PAYLOAD"), "受 maxPerRound 限制时不应注入低相关 artifact");
    }

    @Test
    void shouldUseBoundedRecentFetchWindowWhenMaxPerRoundIsUnlimited() {
        RecordingScriptedRuntimeModel runtimeModel = new RecordingScriptedRuntimeModel(List.of(
                assistant("done")
        ));
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        AiMemoryProvider memoryProvider = memoryId -> new PersistentAiMemory(memoryId, store);
        RecordingFetchLimitArtifactStore artifactStore = new RecordingFetchLimitArtifactStore();

        TestAgent agent = TestAgent.builder()
                .aiChatModel(runtimeModel)
                .aiMemoryProvider(memoryProvider)
                .enableToolResultRehydrate(true)
                .toolResultRehydrateMaxChars(4000)
                .toolResultRehydrateMaxPerRound(0)
                .toolResultArtifactStore(artifactStore)
                .build();

        assertEquals("done", agent.execute("no explicit signal", "conv-bounded-fetch-window"));
        assertEquals(128, artifactStore.lastRecentLimit(),
                "maxPerRound<=0 时 recent 抓取窗口应使用有界上限");
    }

    @Test
    void shouldUseLowerBoundRecentFetchWindowWhenMaxPerRoundIsOne() {
        RecordingScriptedRuntimeModel runtimeModel = new RecordingScriptedRuntimeModel(List.of(
                assistant("done")
        ));
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        AiMemoryProvider memoryProvider = memoryId -> new PersistentAiMemory(memoryId, store);
        RecordingFetchLimitArtifactStore artifactStore = new RecordingFetchLimitArtifactStore();

        TestAgent agent = TestAgent.builder()
                .aiChatModel(runtimeModel)
                .aiMemoryProvider(memoryProvider)
                .enableToolResultRehydrate(true)
                .toolResultRehydrateMaxChars(4000)
                .toolResultRehydrateMaxPerRound(1)
                .toolResultArtifactStore(artifactStore)
                .build();

        assertEquals("done", agent.execute("no explicit signal", "conv-lower-bounded-fetch-window"));
        assertEquals(8, artifactStore.lastRecentLimit(),
                "maxPerRound=1 时 recent 抓取窗口应命中最小下界");
    }

    @Test
    void shouldInjectIndexedRehydrationAsToolObservationRole() {
        RecordingScriptedRuntimeModel runtimeModel = new RecordingScriptedRuntimeModel(List.of(
                assistant("done")
        ));
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        AiMemoryProvider memoryProvider = memoryId -> new PersistentAiMemory(memoryId, store);
        RankedArtifactStore artifactStore = new RankedArtifactStore();

        TestAgent agent = TestAgent.builder()
                .aiChatModel(runtimeModel)
                .aiMemoryProvider(memoryProvider)
                .enableToolResultRehydrate(true)
                .toolResultRehydrateMaxChars(4000)
                .toolResultArtifactStore(artifactStore)
                .build();

        assertEquals("done", agent.execute("browser weather report", "conv-indexed-role"));

        List<AiChatMessage> firstCallMessages = runtimeModel.requests().getFirst().messages();
        long rehydratedToolCount = firstCallMessages.stream()
                .filter(message -> message.role() == AiChatMessage.Role.TOOL)
                .filter(message -> message.content().contains("[Tool Result Rehydrated]"))
                .count();
        assertTrue(rehydratedToolCount > 0, "indexed rehydrate 应以 TOOL 观察消息注入");

        boolean hasSystemRehydrated = firstCallMessages.stream()
                .filter(message -> message.role() == AiChatMessage.Role.SYSTEM)
                .anyMatch(message -> message.content().contains("[Tool Result Rehydrated]"));
        assertFalse(hasSystemRehydrated, "indexed rehydrate 不应再注入 SYSTEM 消息");
    }

    @Test
    void shouldNotInjectDuplicateArtifactWhenAlreadyRehydratedInSameRound() {
        RecordingScriptedRuntimeModel runtimeModel = new RecordingScriptedRuntimeModel(List.of(
                assistant("done")
        ));
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        String duplicatedId = DuplicateAwareArtifactStore.DUPLICATED_ID;
        store.append("conv-avoid-duplicate-rehydrate",
                new AiChatMessage(
                        AiChatMessage.Role.TOOL,
                        """
                                [Tool Result Offloaded]
                                tool=browser
                                toolCallId=call_1
                                artifactId=%s
                                originalChars=1024
                                previewHead:
                                head
                                previewTail:
                                tail
                                """.formatted(duplicatedId),
                        "browser",
                        "call_1",
                        List.of()
                ));
        AiMemoryProvider memoryProvider = memoryId -> new PersistentAiMemory(memoryId, store);
        DuplicateAwareArtifactStore artifactStore = new DuplicateAwareArtifactStore();

        TestAgent agent = TestAgent.builder()
                .aiChatModel(runtimeModel)
                .aiMemoryProvider(memoryProvider)
                .enableToolResultRehydrate(true)
                .toolResultRehydrateMaxChars(4000)
                .toolResultRehydrateMaxPerRound(2)
                .toolResultArtifactStore(artifactStore)
                .build();

        assertEquals("done", agent.execute("browser details", "conv-avoid-duplicate-rehydrate"));

        List<AiChatMessage> firstCallMessages = runtimeModel.requests().getFirst().messages();
        long duplicatedPayloadCount = firstCallMessages.stream()
                .filter(message -> message.role() == AiChatMessage.Role.TOOL)
                .map(AiChatMessage::content)
                .filter(content -> content != null && content.contains("DUPLICATED_PAYLOAD"))
                .count();
        assertEquals(1, duplicatedPayloadCount, "同一 artifact 在单轮中不应重复回填");
        assertTrue(firstCallMessages.stream()
                .filter(message -> message.role() == AiChatMessage.Role.TOOL)
                .map(AiChatMessage::content)
                .anyMatch(content -> content != null && content.contains("SECOND_PAYLOAD")),
                "去重后应允许注入下一个候选 artifact");
    }

    @Test
    void shouldSkipOversizedIndexedRehydratePayloadPerItemBudget() {
        RecordingScriptedRuntimeModel runtimeModel = new RecordingScriptedRuntimeModel(List.of(
                assistant("done")
        ));
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        AiMemoryProvider memoryProvider = memoryId -> new PersistentAiMemory(memoryId, store);
        OversizedIndexedArtifactStore artifactStore = new OversizedIndexedArtifactStore();

        TestAgent agent = TestAgent.builder()
                .aiChatModel(runtimeModel)
                .aiMemoryProvider(memoryProvider)
                .enableToolResultRehydrate(true)
                .toolResultRehydrateMaxChars(256)
                .toolResultArtifactStore(artifactStore)
                .build();

        String artifactId = OversizedIndexedArtifactStore.OVERSIZED_ID;
        assertEquals("done", agent.execute("please rehydrate " + artifactId, "conv-indexed-char-budget"));

        String payload = runtimeModel.requests().getFirst().messages().toString();
        assertFalse(payload.contains("[Tool Result Rehydrated]"), "单条回填超过字符上限时应跳过注入");
    }

    @Test
    void shouldSkipIndexedRehydrationWhenNoExplicitSignal() {
        RecordingScriptedRuntimeModel runtimeModel = new RecordingScriptedRuntimeModel(List.of(
                assistant("done")
        ));
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        AiMemoryProvider memoryProvider = memoryId -> new PersistentAiMemory(memoryId, store);
        RankedArtifactStore artifactStore = new RankedArtifactStore();

        TestAgent agent = TestAgent.builder()
                .aiChatModel(runtimeModel)
                .aiMemoryProvider(memoryProvider)
                .enableToolResultRehydrate(true)
                .toolResultRehydrateMaxChars(4000)
                .toolResultArtifactStore(artifactStore)
                .build();

        assertEquals("done", agent.execute("just greeting", "conv-no-rehydrate-signal"));

        String payload = runtimeModel.requests().getFirst().messages().toString();
        assertFalse(payload.contains("[Tool Result Rehydrated]"), "没有明确 artifactId/tool 关联时不应注入回填");
    }

    @Test
    void shouldApplyBudgetAfterIndexedRehydrationInjection() {
        RecordingScriptedRuntimeModel runtimeModel = new RecordingScriptedRuntimeModel(List.of(
                assistant("done")
        ));
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        AiMemoryProvider memoryProvider = memoryId -> new PersistentAiMemory(memoryId, store);
        ExplicitArtifactStore artifactStore = new ExplicitArtifactStore();

        TestAgent agent = TestAgent.builder()
                .aiChatModel(runtimeModel)
                .aiMemoryProvider(memoryProvider)
                .enableToolResultRehydrate(true)
                .toolResultRehydrateMaxChars(4000)
                .modelContextMaxTotalMessages(2)
                .toolResultArtifactStore(artifactStore)
                .build();

        String artifactId = ExplicitArtifactStore.EXPLICIT_ID;
        assertEquals("done", agent.execute("please rehydrate " + artifactId, "conv-budget-after-rehydrate"));

        List<AiChatMessage> firstCallMessages = runtimeModel.requests().getFirst().messages();
        assertEquals(2, firstCallMessages.size(), "回填后仍应执行总量预算裁剪");
        assertTrue(firstCallMessages.stream().anyMatch(message -> message.role() == AiChatMessage.Role.USER),
                "预算裁剪后应保留当前用户消息");
        assertTrue(firstCallMessages.stream().anyMatch(message ->
                        message.role() == AiChatMessage.Role.TOOL
                                && message.content() != null
                                && message.content().contains("[Tool Result Rehydrated]")),
                "预算裁剪后应保留最新回填 TOOL 观察消息");
    }

    @Test
    void shouldRehydrateByCompressedCardSummarySignal() {
        RecordingScriptedRuntimeModel runtimeModel = new RecordingScriptedRuntimeModel(List.of(
                assistant("done")
        ));
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        String artifactId = ExplicitArtifactStore.EXPLICIT_ID;
        store.append("conv-summary-signal",
                new AiChatMessage(
                        AiChatMessage.Role.TOOL,
                        """
                                FACT: weather station cache ready
                                ACTION: refreshed seattle report
                                TODO: compare hourly trend before final answer
                                artifactId=%s
                                %s
                                """.formatted(artifactId, "X".repeat(2600)),
                        "browser",
                        "call_1",
                        List.of()
                ));
        AiMemoryProvider memoryProvider = memoryId -> new PersistentAiMemory(memoryId, store);
        ExplicitArtifactStore artifactStore = new ExplicitArtifactStore();

        TestAgent agent = TestAgent.builder()
                .aiChatModel(runtimeModel)
                .aiMemoryProvider(memoryProvider)
                .enableToolResultRehydrate(true)
                .toolResultRehydrateMaxChars(4000)
                .toolResultArtifactStore(artifactStore)
                .build();

        assertEquals("done", agent.execute("please compare hourly trend before final answer", "conv-summary-signal"));

        String payload = runtimeModel.requests().getFirst().messages().toString();
        assertTrue(payload.contains("[Tool Result Context Compressed]"), "超长原始工具结果应先压缩进入模型上下文");
        assertTrue(payload.contains("[Tool Result Rehydrated]"), "命中压缩摘要后应注入对应 artifact 回填块");
        assertTrue(payload.contains("EXPLICIT_PAYLOAD"), "回填块应载入 artifact 原始内容");
    }

    @Test
    void shouldPersistSystemMessageOnlyOnceOnConcurrentFirstTurns() throws Exception {
        AiChatModel runtimeModel = request -> assistant("ok");
        var dir = Files.createTempDirectory("agent-system-message-once-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);
        AiMemoryProvider memoryProvider = memoryId -> new PersistentAiMemory(memoryId, store);

        TestAgent agent = TestAgent.builder()
                .aiChatModel(runtimeModel)
                .aiMemoryProvider(memoryProvider)
                .build();

        int tasks = 20;
        ExecutorService executor = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(tasks);
        for (int i = 0; i < tasks; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    start.await(10, TimeUnit.SECONDS);
                    agent.execute("q-" + idx, "conv-system-once");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(20, TimeUnit.SECONDS), "并发首轮请求未在预期时间内完成");
        executor.shutdownNow();

        AiMemory memory = memoryProvider.get("conv-system-once");
        long systemCount = memory.messages().stream()
                .filter(message -> message.role() == AiChatMessage.Role.SYSTEM)
                .count();
        assertEquals(1, systemCount, "同一会话的 SystemMessage 应只写入一次");
    }

    private static AiChatResponse assistant(String text) {
        return new AiChatResponse(
                AiChatMessage.assistant(text),
                null,
                null,
                null,
                null,
                null
        );
    }

    private static AiChatResponse assistantToolCall(String id, String name, String arguments) {
        return new AiChatResponse(
                AiChatMessage.assistant("tool", List.of(new AiToolCall(id, name, arguments))),
                null,
                null,
                null,
                null,
                null
        );
    }

    static class RecordingScriptedRuntimeModel implements AiChatModel {
        private final List<AiChatResponse> responses;
        private final List<AiChatRequest> requests = new ArrayList<>();
        private int cursor = 0;

        RecordingScriptedRuntimeModel(List<AiChatResponse> responses) {
            this.responses = responses;
        }

        @Override
        public synchronized AiChatResponse chat(AiChatRequest request) {
            requests.add(request);
            if (cursor >= responses.size()) {
                return assistant("done");
            }
            return responses.get(cursor++);
        }

        List<AiChatRequest> requests() {
            return requests;
        }
    }

    static class EchoTool {
        @AiTool("回显工具")
        public String echo(@AiParam("文本") String text) {
            return "echo-result:" + text;
        }
    }

    static class LargeOutputTool {
        @AiTool("生成大文本")
        public String dumpLarge() {
            return "L".repeat(3000);
        }
    }

    static class InMemoryArtifactStore implements AiToolResultArtifactStore {
        private final Map<String, String> artifacts = new ConcurrentHashMap<>();
        private final Map<Object, List<ToolResultArtifactRef>> recentRefs = new ConcurrentHashMap<>();

        @Override
        public String save(Object memoryId, String toolName, String toolArguments, String outcome) {
            String hash = Integer.toHexString(outcome.hashCode());
            String padded = "0".repeat(Math.max(0, 64 - hash.length())) + hash;
            String id = "sha256:" + padded;
            artifacts.putIfAbsent(id, outcome);
            recentRefs.compute(memoryId, (key, refs) -> {
                List<ToolResultArtifactRef> updated = refs == null ? new ArrayList<>() : new ArrayList<>(refs);
                updated.add(0, new ToolResultArtifactRef(
                        id,
                        toolName,
                        toolArguments,
                        outcome == null ? 0 : outcome.length(),
                        System.currentTimeMillis()
                ));
                return updated;
            });
            return id;
        }

        @Override
        public Optional<String> load(String artifactId) {
            return Optional.ofNullable(artifacts.get(artifactId));
        }

        @Override
        public List<ToolResultArtifactRef> recent(Object memoryId, int limit) {
            List<ToolResultArtifactRef> refs = recentRefs.get(memoryId);
            if (refs == null || refs.isEmpty() || limit <= 0) {
                return List.of();
            }
            return refs.subList(0, Math.min(limit, refs.size()));
        }

        int size() {
            return artifacts.size();
        }

        String firstPayload() {
            return artifacts.values().stream().findFirst().orElse("");
        }
    }

    static class MalformedIdTrackingArtifactStore implements AiToolResultArtifactStore {
        private final AtomicInteger loadCalls = new AtomicInteger();

        @Override
        public String save(Object memoryId, String toolName, String toolArguments, String outcome) {
            return "sha256:" + "0".repeat(64);
        }

        @Override
        public Optional<String> load(String artifactId) {
            loadCalls.incrementAndGet();
            return Optional.of("SHOULD_NOT_BE_LOADED");
        }

        @Override
        public List<ToolResultArtifactRef> recent(Object memoryId, int limit) {
            return List.of(new ToolResultArtifactRef(
                    "sha256:deadbeef",
                    "dumpLarge",
                    "{}",
                    1024,
                    System.currentTimeMillis()
            ));
        }

        int loadCalls() {
            return loadCalls.get();
        }
    }

    static class RankedArtifactStore implements AiToolResultArtifactStore {
        private static final String RELEVANT_ID = "sha256:" + "a".repeat(64);
        private static final String IRRELEVANT_ID = "sha256:" + "b".repeat(64);

        @Override
        public String save(Object memoryId, String toolName, String toolArguments, String outcome) {
            return RELEVANT_ID;
        }

        @Override
        public Optional<String> load(String artifactId) {
            if (RELEVANT_ID.equals(artifactId)) {
                return Optional.of("RELEVANT_PAYLOAD");
            }
            if (IRRELEVANT_ID.equals(artifactId)) {
                return Optional.of("IRRELEVANT_PAYLOAD");
            }
            return Optional.empty();
        }

        @Override
        public List<ToolResultArtifactRef> recent(Object memoryId, int limit) {
            return List.of(
                    new ToolResultArtifactRef(IRRELEVANT_ID, "browser", "latest stock", 18, 100L),
                    new ToolResultArtifactRef(RELEVANT_ID, "browser", "weather report", 16, 200L)
            );
        }
    }

    static class ExplicitArtifactStore implements AiToolResultArtifactStore {
        static final String EXPLICIT_ID = "sha256:" + "c".repeat(64);

        @Override
        public String save(Object memoryId, String toolName, String toolArguments, String outcome) {
            return EXPLICIT_ID;
        }

        @Override
        public Optional<String> load(String artifactId) {
            if (EXPLICIT_ID.equals(artifactId)) {
                return Optional.of("EXPLICIT_PAYLOAD");
            }
            return Optional.empty();
        }

        @Override
        public List<ToolResultArtifactRef> recent(Object memoryId, int limit) {
            return List.of(new ToolResultArtifactRef(EXPLICIT_ID, "browser", "details", 16, 300L));
        }
    }

    static class DuplicateAwareArtifactStore implements AiToolResultArtifactStore {
        static final String DUPLICATED_ID = "sha256:" + "d".repeat(64);
        static final String SECOND_ID = "sha256:" + "e".repeat(64);

        @Override
        public String save(Object memoryId, String toolName, String toolArguments, String outcome) {
            return DUPLICATED_ID;
        }

        @Override
        public Optional<String> load(String artifactId) {
            if (DUPLICATED_ID.equals(artifactId)) {
                return Optional.of("DUPLICATED_PAYLOAD");
            }
            if (SECOND_ID.equals(artifactId)) {
                return Optional.of("SECOND_PAYLOAD");
            }
            return Optional.empty();
        }

        @Override
        public List<ToolResultArtifactRef> recent(Object memoryId, int limit) {
            return List.of(
                    new ToolResultArtifactRef(DUPLICATED_ID, "browser", "details", 1200, 200L),
                    new ToolResultArtifactRef(SECOND_ID, "browser", "details", 1000, 100L)
            );
        }
    }

    static class OversizedIndexedArtifactStore implements AiToolResultArtifactStore {
        static final String OVERSIZED_ID = "sha256:" + "f".repeat(64);

        @Override
        public String save(Object memoryId, String toolName, String toolArguments, String outcome) {
            return OVERSIZED_ID;
        }

        @Override
        public Optional<String> load(String artifactId) {
            if (OVERSIZED_ID.equals(artifactId)) {
                return Optional.of("X".repeat(600));
            }
            return Optional.empty();
        }

        @Override
        public List<ToolResultArtifactRef> recent(Object memoryId, int limit) {
            return List.of(new ToolResultArtifactRef(OVERSIZED_ID, "browser", "details", 600, 100L));
        }
    }

    static class RecordingFetchLimitArtifactStore implements AiToolResultArtifactStore {
        private final AtomicInteger lastRecentLimit = new AtomicInteger(-1);

        @Override
        public String save(Object memoryId, String toolName, String toolArguments, String outcome) {
            return "sha256:" + "1".repeat(64);
        }

        @Override
        public Optional<String> load(String artifactId) {
            return Optional.empty();
        }

        @Override
        public List<ToolResultArtifactRef> recent(Object memoryId, int limit) {
            lastRecentLimit.set(limit);
            return List.of();
        }

        int lastRecentLimit() {
            return lastRecentLimit.get();
        }
    }

    static class TestAgent extends AbstractAgentExecutor<TestAgent.Builder> {

        static class Builder extends AbstractAgentExecutor.Builder<Builder> {
            public TestAgent build() {
                this.name("test_agent")
                        .description("test agent")
                        .singleParameter("input")
                        .systemMessage("you are a test agent");
                return new TestAgent(this);
            }
        }

        public static Builder builder() {
            return new Builder();
        }

        private TestAgent(Builder builder) {
            super(builder);
        }
    }

    private static String extractArtifactId(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        for (String line : content.split("\\R")) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.startsWith("artifactId=")) {
                return trimmed.substring("artifactId=".length()).trim();
            }
        }
        return null;
    }
}
