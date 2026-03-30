package com.openmanus.agent.base;

import com.openmanus.infra.memory.FileChatMemoryStore;
import com.openmanus.infra.memory.FileToolResultArtifactStore;
import com.openmanus.infra.memory.PersistentChatMemory;
import com.openmanus.infra.memory.ToolResultArtifactStore;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbstractAgentExecutorChatMemoryIntegrationTest {

    @Test
    void shouldPersistToolResultIntoChatMemoryAndReuseInNextTurn() {
        ChatModel chatModel = mock(ChatModel.class);
        InMemoryChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(50)
                .chatMemoryStore(chatMemoryStore)
                .build();

        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .name("echo")
                .arguments("{\"text\":\"hello\"}")
                .build();

        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(toolCall)).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("first-done")).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("second-done")).build());

        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .toolFromObject(new EchoTool())
                .build();

        String memoryId = "conv-tool-memory";
        String firstResult = agent.execute(requestOf("请调用工具"), memoryId);
        String secondResult = agent.execute(requestOf("你还记得上轮工具输出吗"), memoryId);

        assertEquals("first-done", firstResult);
        assertEquals("second-done", secondResult);

        ChatMemory memory = memoryProvider.get(memoryId);
        boolean hasToolResultMessage = memory.messages().stream()
                .filter(message -> message instanceof ToolExecutionResultMessage)
                .map(message -> (ToolExecutionResultMessage) message)
                .anyMatch(message -> "echo".equals(message.toolName())
                        && message.text().contains("echo-result:hello"));
        assertTrue(hasToolResultMessage, "工具结果应写入 ChatMemory");

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(3)).chat(requestCaptor.capture());
        ChatRequest thirdModelCall = requestCaptor.getAllValues().get(2);

        boolean thirdCallContainsPreviousToolResult = thirdModelCall.messages().stream()
                .filter(message -> message instanceof ToolExecutionResultMessage)
                .map(message -> (ToolExecutionResultMessage) message)
                .anyMatch(message -> message.text().contains("echo-result:hello"));
        assertTrue(thirdCallContainsPreviousToolResult, "下一轮请求应携带上一轮工具结果上下文");
    }

    @Test
    void shouldCompactLargeToolResultBeforePersistingIntoMemory() {
        ChatModel chatModel = mock(ChatModel.class);
        InMemoryChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(50)
                .chatMemoryStore(chatMemoryStore)
                .build();

        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .name("dumpLarge")
                .arguments("{}")
                .build();

        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(toolCall)).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("done")).build());

        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .enableToolResultCompaction(true)
                .memoryToolResultMaxChars(256)
                .compactToolResultHeadChars(80)
                .compactToolResultTailChars(40)
                .toolFromObject(new LargeOutputTool())
                .build();

        String memoryId = "conv-compact-tool-result";
        String result = agent.execute(requestOf("请生成大结果"), memoryId);
        assertEquals("done", result);

        ChatMemory memory = memoryProvider.get(memoryId);
        String persistedToolResult = memory.messages().stream()
                .filter(message -> message instanceof ToolExecutionResultMessage)
                .map(message -> (ToolExecutionResultMessage) message)
                .map(ToolExecutionResultMessage::text)
                .findFirst()
                .orElse("");
        assertTrue(persistedToolResult.contains("[Tool Result Compacted]"), "超长工具结果应压缩写入 memory");
        assertTrue(persistedToolResult.contains("sha256="), "压缩结果应保留可追溯摘要信息");
    }

    @Test
    void shouldUseCompactedToolResultInNextIterationWithinSameExecute() {
        ChatModel chatModel = mock(ChatModel.class);
        InMemoryChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(100)
                .chatMemoryStore(chatMemoryStore)
                .build();

        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .name("dumpLarge")
                .arguments("{}")
                .build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(toolCall)).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("done")).build());

        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .enableToolResultCompaction(true)
                .memoryToolResultMaxChars(256)
                .compactToolResultHeadChars(64)
                .compactToolResultTailChars(32)
                .toolFromObject(new LargeOutputTool())
                .build();

        assertEquals("done", agent.execute(requestOf("run"), "conv-compact-same-execute"));

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(2)).chat(requestCaptor.capture());
        String secondPayload = requestCaptor.getAllValues().get(1).messages().toString();
        assertTrue(secondPayload.contains("[Tool Result Compacted]"),
                "同一 execute 的下一轮也应使用压缩后的工具结果，避免上下文膨胀");
    }

    @Test
    void shouldOffloadLargeToolResultLosslesslyWhenEnabled() {
        ChatModel chatModel = mock(ChatModel.class);
        InMemoryChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(50)
                .chatMemoryStore(chatMemoryStore)
                .build();

        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .name("dumpLarge")
                .arguments("{}")
                .build();

        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(toolCall)).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("done")).build());

        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .enableToolResultOffload(true)
                .toolResultOffloadMinChars(256)
                .toolResultArtifactStore(artifactStore)
                .toolFromObject(new LargeOutputTool())
                .build();

        String memoryId = "conv-offload-tool-result";
        String result = agent.execute(requestOf("请卸载大结果"), memoryId);
        assertEquals("done", result);

        ChatMemory memory = memoryProvider.get(memoryId);
        String persistedToolResult = memory.messages().stream()
                .filter(message -> message instanceof ToolExecutionResultMessage)
                .map(message -> (ToolExecutionResultMessage) message)
                .map(ToolExecutionResultMessage::text)
                .findFirst()
                .orElse("");
        assertTrue(persistedToolResult.contains("[Tool Result Offloaded]"), "开启卸载后应写入 offload 索引卡片");
        assertTrue(persistedToolResult.contains("artifactId=sha256:"), "offload 卡片应包含 artifactId");
        assertEquals(1, artifactStore.size(), "应保存一份无损 artifact");
        assertTrue(artifactStore.firstPayload().length() >= 3000, "artifact 应保留完整工具输出");
    }

    @Test
    void shouldRespectConfiguredOffloadPreviewHeadAndTailChars() {
        ChatModel chatModel = mock(ChatModel.class);
        InMemoryChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(50)
                .chatMemoryStore(chatMemoryStore)
                .build();

        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .name("dumpLarge")
                .arguments("{}")
                .build();

        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(toolCall)).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("done")).build());

        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .enableToolResultOffload(true)
                .toolResultOffloadMinChars(256)
                .toolResultOffloadHeadChars(120)
                .toolResultOffloadTailChars(90)
                .toolResultArtifactStore(artifactStore)
                .toolFromObject(new LargeOutputTool())
                .build();

        String memoryId = "conv-offload-preview-config";
        assertEquals("done", agent.execute(requestOf("请卸载大结果"), memoryId));

        String persistedToolResult = memoryProvider.get(memoryId).messages().stream()
                .filter(message -> message instanceof ToolExecutionResultMessage)
                .map(message -> (ToolExecutionResultMessage) message)
                .map(ToolExecutionResultMessage::text)
                .findFirst()
                .orElse("");

        String previewHead = extractBlock(persistedToolResult, "previewHead:\n", "\npreviewTail:\n");
        String previewTail = extractTailBlock(persistedToolResult, "previewTail:\n");
        assertEquals(120, previewHead.length(), "offload 卡片预览前缀长度应使用配置值");
        assertEquals(90, previewTail.length(), "offload 卡片预览后缀长度应使用配置值");
    }

    @Test
    void shouldPreferFullToolResultInNextIterationWhenWithinRehydrateBudget() {
        ChatModel chatModel = mock(ChatModel.class);
        InMemoryChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(100)
                .chatMemoryStore(chatMemoryStore)
                .build();

        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .name("dumpLarge")
                .arguments("{}")
                .build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(toolCall)).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("done")).build());

        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .enableToolResultOffload(true)
                .toolResultOffloadMinChars(256)
                .toolResultArtifactStore(artifactStore)
                .toolFromObject(new LargeOutputTool())
                .build();

        assertEquals("done", agent.execute(requestOf("run"), "conv-offload-same-execute"));

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(2)).chat(requestCaptor.capture());
        String secondPayload = requestCaptor.getAllValues().get(1).messages().toString();
        assertTrue(secondPayload.contains("L".repeat(300)),
                "同一 execute 的下一轮应优先保留可用工具结果，保障工具链连续性");
        assertFalse(secondPayload.contains("[Tool Result Offloaded]"),
                "当工具结果仍在回填预算内时，不应仅保留 offload 卡片");
    }

    @Test
    void shouldUseLoopContinuitySnapshotWhenToolResultExceedsRehydrateBudget() {
        ChatModel chatModel = mock(ChatModel.class);
        InMemoryChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(100)
                .chatMemoryStore(chatMemoryStore)
                .build();

        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .name("dumpLarge")
                .arguments("{}")
                .build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(toolCall)).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("done")).build());

        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .enableToolResultOffload(true)
                .toolResultOffloadMinChars(256)
                .enableToolResultRehydrate(true)
                .toolResultRehydrateMaxChars(512)
                .toolResultArtifactStore(artifactStore)
                .toolFromObject(new LargeOutputTool())
                .build();

        assertEquals("done", agent.execute(requestOf("run"), "conv-offload-loop-continuity"));

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(2)).chat(requestCaptor.capture());
        String secondPayload = requestCaptor.getAllValues().get(1).messages().toString();
        assertTrue(secondPayload.contains("[Tool Result Offloaded]"),
                "超出回填预算时应发送受控快照而不是完整工具输出");
        assertTrue(secondPayload.contains("source=loop-continuity"),
                "应标记 loop-continuity 来源，便于排查长上下文治理行为");
    }

    @Test
    void shouldRehydrateOffloadedToolResultIntoModelInputWhenEnabled() {
        ChatModel chatModel = mock(ChatModel.class);
        InMemoryChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(200)
                .chatMemoryStore(chatMemoryStore)
                .build();

        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .name("dumpLarge")
                .arguments("{}")
                .build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(toolCall)).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("first-done")).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("second-done")).build());

        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .enableToolResultOffload(true)
                .toolResultOffloadMinChars(256)
                .enableToolResultRehydrate(true)
                .toolResultRehydrateMaxChars(4000)
                .toolResultArtifactStore(artifactStore)
                .toolFromObject(new LargeOutputTool())
                .build();

        String memoryId = "conv-offload-rehydrate";
        assertEquals("first-done", agent.execute(requestOf("first-question"), memoryId));
        assertEquals("second-done", agent.execute(requestOf("second-question"), memoryId));

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(3)).chat(requestCaptor.capture());
        ChatRequest thirdCall = requestCaptor.getAllValues().get(2);
        String payload = thirdCall.messages().toString();
        assertTrue(payload.contains("[Tool Result Rehydrated]"), "回填开启后应向模型输入注入回填内容");
        assertTrue(payload.contains("artifactId=sha256:"), "回填块应保留 artifactId");
    }

    @Test
    void shouldInjectRehydrationFromArtifactIndexWhenHistoryWindowDropsToolCards() throws Exception {
        ChatModel chatModel = mock(ChatModel.class);
        InMemoryChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(200)
                .chatMemoryStore(chatMemoryStore)
                .build();

        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .name("dumpLarge")
                .arguments("{}")
                .build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(toolCall)).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("first-done")).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("second-done")).build());

        Path artifactDir = Files.createTempDirectory("tool-artifacts-indexed-");
        ToolResultArtifactStore artifactStore = new FileToolResultArtifactStore(artifactDir);
        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .enableToolResultOffload(true)
                .toolResultOffloadMinChars(256)
                .enableToolResultRehydrate(true)
                .toolResultRehydrateMaxChars(4000)
                .toolResultRehydrateMaxPerRound(1)
                .modelContextMaxMessages(1)
                .modelContextMaxTotalMessages(3)
                .toolResultArtifactStore(artifactStore)
                .toolFromObject(new LargeOutputTool())
                .build();

        String memoryId = "conv-offload-indexed-rehydrate";
        assertEquals("first-done", agent.execute(requestOf("first-question"), memoryId));
        assertEquals("second-done", agent.execute(requestOf("second-question"), memoryId));

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(3)).chat(requestCaptor.capture());
        ChatRequest thirdCall = requestCaptor.getAllValues().get(2);
        String payload = thirdCall.messages().toString();
        assertTrue(payload.contains("[Tool Result Rehydrated]"),
                "历史窗口裁剪掉工具卡片时，应从 artifact 索引补充回填");
        assertTrue(payload.contains("source=index"),
                "索引回填消息应标记来源，便于排查上下文治理策略");
    }

    @Test
    void shouldPreferRelevantArtifactWhenInjectingIndexedRehydration() throws Exception {
        ChatModel chatModel = mock(ChatModel.class);
        InMemoryChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(200)
                .chatMemoryStore(chatMemoryStore)
                .build();

        ToolExecutionRequest pythonCall = ToolExecutionRequest.builder()
                .name("dumpPythonLarge")
                .arguments("{\"topic\":\"python\"}")
                .build();
        ToolExecutionRequest browserCall = ToolExecutionRequest.builder()
                .name("dumpBrowserLarge")
                .arguments("{\"topic\":\"browser\"}")
                .build();

        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(pythonCall)).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("first-done")).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(browserCall)).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("second-done")).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("third-done")).build());

        Path artifactDir = Files.createTempDirectory("tool-artifacts-relevance-");
        ToolResultArtifactStore artifactStore = new FileToolResultArtifactStore(artifactDir);
        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .enableToolResultOffload(true)
                .toolResultOffloadMinChars(256)
                .enableToolResultRehydrate(true)
                .toolResultRehydrateMaxChars(5000)
                .toolResultRehydrateMaxPerRound(1)
                .modelContextMaxMessages(1)
                .modelContextMaxTotalMessages(4)
                .toolResultArtifactStore(artifactStore)
                .toolFromObject(new DomainLargeOutputTool())
                .build();

        String memoryId = "conv-offload-indexed-relevance";
        assertEquals("first-done", agent.execute(requestOf("先做 python 任务"), memoryId));
        assertEquals("second-done", agent.execute(requestOf("再做 browser 任务"), memoryId));
        assertEquals("third-done", agent.execute(requestOf("现在继续 python 分析"), memoryId));

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(5)).chat(requestCaptor.capture());
        ChatRequest fifthCall = requestCaptor.getAllValues().get(4);
        String payload = fifthCall.messages().toString();

        assertTrue(payload.contains("[Tool Result Rehydrated]"));
        assertTrue(payload.contains("source=index"));
        assertTrue(payload.contains("tool=dumpPythonLarge"),
                "索引回填应优先选择与当前问题更相关的 artifact");
        assertFalse(payload.contains("tool=dumpBrowserLarge"),
                "maxPerRound=1 下不应注入不相关的 artifact");
    }

    @Test
    void shouldNotInjectDuplicateRehydrationWhenIndexContainsSameArtifactId() throws Exception {
        ChatModel chatModel = mock(ChatModel.class);
        InMemoryChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(200)
                .chatMemoryStore(chatMemoryStore)
                .build();

        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .name("dumpLarge")
                .arguments("{}")
                .build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(toolCall)).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("first-done")).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(toolCall)).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("second-done")).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("third-done")).build());

        Path artifactDir = Files.createTempDirectory("tool-artifacts-dedup-inject-");
        ToolResultArtifactStore artifactStore = new FileToolResultArtifactStore(artifactDir);
        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .enableToolResultOffload(true)
                .toolResultOffloadMinChars(256)
                .enableToolResultRehydrate(true)
                .toolResultRehydrateMaxChars(5000)
                .toolResultRehydrateMaxPerRound(2)
                .modelContextMaxMessages(1)
                .modelContextMaxTotalMessages(6)
                .toolResultArtifactStore(artifactStore)
                .toolFromObject(new LargeOutputTool())
                .build();

        String memoryId = "conv-offload-indexed-dedup-inject";
        assertEquals("first-done", agent.execute(requestOf("first"), memoryId));
        assertEquals("second-done", agent.execute(requestOf("second"), memoryId));
        assertEquals("third-done", agent.execute(requestOf("third"), memoryId));

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(5)).chat(requestCaptor.capture());
        String payload = requestCaptor.getAllValues().get(4).messages().toString();

        int rehydratedCount = countOccurrences(payload, "[Tool Result Rehydrated]");
        assertEquals(1, rehydratedCount, "相同 artifactId 不应重复注入回填内容");
    }

    @Test
    void shouldKeepFullLargeToolResultByDefault() {
        ChatModel chatModel = mock(ChatModel.class);
        InMemoryChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(50)
                .chatMemoryStore(chatMemoryStore)
                .build();

        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .name("dumpLarge")
                .arguments("{}")
                .build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(toolCall)).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("done")).build());

        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .toolFromObject(new LargeOutputTool())
                .build();

        agent.execute(requestOf("默认不压缩"), "conv-no-compact-default");

        String persistedToolResult = memoryProvider.get("conv-no-compact-default").messages().stream()
                .filter(message -> message instanceof ToolExecutionResultMessage)
                .map(message -> (ToolExecutionResultMessage) message)
                .map(ToolExecutionResultMessage::text)
                .findFirst()
                .orElse("");
        assertFalse(persistedToolResult.contains("[Tool Result Compacted]"), "默认应保留完整工具结果");
        assertEquals(3000, persistedToolResult.length(), "默认应写入完整工具结果文本");
    }

    @Test
    void shouldRemainCompatibleWithLegacyContextJsonArguments() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());

        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .build();

        String result = agent.execute(legacyRequestOf("legacy-input"), "conv-legacy");
        assertEquals("ok", result);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(1)).chat(requestCaptor.capture());
        ChatRequest firstCall = requestCaptor.getValue();

        boolean containsLegacyInput = firstCall.messages().stream()
                .anyMatch(message -> message.toString().contains("legacy-input"));
        assertTrue(containsLegacyInput, "应从 legacy JSON 参数中提取 context 字段");
    }

    @Test
    void shouldNotRewriteJsonTextWithAdditionalFields() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());

        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .build();

        String mixedJson = "{\"context\":\"user-json\",\"other\":1}";
        String result = agent.execute(requestOf(mixedJson), "conv-mixed-json");
        assertEquals("ok", result);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(1)).chat(requestCaptor.capture());
        ChatRequest firstCall = requestCaptor.getValue();

        boolean containsOriginalJsonText = firstCall.messages().stream()
                .anyMatch(message -> message.toString().contains(mixedJson));
        assertTrue(containsOriginalJsonText, "包含额外字段的 JSON 文本不应被当作 legacy context 解包");
    }

    @Test
    void shouldNotRewriteRegularJsonText() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());

        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .build();

        String plainJsonText = "{\"foo\":\"bar\"}";
        String result = agent.execute(requestOf(plainJsonText), "conv-plain-json");
        assertEquals("ok", result);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(1)).chat(requestCaptor.capture());
        ChatRequest firstCall = requestCaptor.getValue();

        boolean containsOriginalJsonText = firstCall.messages().stream()
                .anyMatch(message -> message.toString().contains(plainJsonText));
        assertTrue(containsOriginalJsonText, "普通 JSON 文本应保持原样传递");
    }

    @Test
    void shouldRejectNullArguments() {
        ChatModel chatModel = mock(ChatModel.class);
        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .build();

        ToolExecutionRequest nullArgsRequest = ToolExecutionRequest.builder()
                .name("test_agent")
                .arguments(null)
                .build();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> agent.execute(nullArgsRequest, "conv-null-args"));
        assertEquals("toolExecutionRequest.arguments cannot be null or blank", ex.getMessage());
        verify(chatModel, never()).chat(any(ChatRequest.class));
    }

    @Test
    void shouldRejectBlankArguments() {
        ChatModel chatModel = mock(ChatModel.class);
        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .build();

        ToolExecutionRequest blankArgsRequest = ToolExecutionRequest.builder()
                .name("test_agent")
                .arguments("   ")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> agent.execute(blankArgsRequest, "conv-blank-args"));
        assertEquals("toolExecutionRequest.arguments cannot be null or blank", ex.getMessage());
        verify(chatModel, never()).chat(any(ChatRequest.class));
    }

    @Test
    void shouldPassFullChatMemoryHistoryWithoutSummaryRewrite() {
        ChatModel chatModel = mock(ChatModel.class);
        InMemoryChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(100)
                .chatMemoryStore(chatMemoryStore)
                .build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());

        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .build();

        String memoryId = "conv-full-history";
        ChatMemory memory = memoryProvider.get(memoryId);
        memory.add(UserMessage.from("old-user-message"));
        memory.add(AiMessage.from("old-ai-message"));
        memory.add(UserMessage.from("recent-user-message"));

        String result = agent.execute(requestOf("new-question"), memoryId);
        assertEquals("ok", result);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(1)).chat(requestCaptor.capture());
        ChatRequest firstCall = requestCaptor.getValue();

        boolean hasSummaryMessage = firstCall.messages().stream()
                .anyMatch(message -> message.toString().contains("[Context Summary]"));
        assertFalse(hasSummaryMessage, "单智能体模式不应注入字符串摘要上下文");

        boolean keepsOldMessage = firstCall.messages().stream()
                .anyMatch(message -> message.toString().contains("old-user-message"));
        assertTrue(keepsOldMessage, "历史消息应原样保留");

        boolean keepsRecentMessage = firstCall.messages().stream()
                .anyMatch(message -> message.toString().contains("recent-user-message"));
        assertTrue(keepsRecentMessage, "应保留最近消息原文");
    }

    @Test
    void shouldLimitModelContextMessagesButKeepPersistentHistory() {
        ChatModel chatModel = mock(ChatModel.class);
        InMemoryChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(200)
                .chatMemoryStore(chatMemoryStore)
                .build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());

        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .modelContextMaxMessages(3)
                .build();

        String memoryId = "conv-model-context-limit";
        ChatMemory memory = memoryProvider.get(memoryId);
        memory.add(SystemMessage.from("you are a test agent"));
        memory.add(UserMessage.from("old-1"));
        memory.add(AiMessage.from("old-2"));
        memory.add(UserMessage.from("old-3"));
        memory.add(AiMessage.from("old-4"));

        String result = agent.execute(requestOf("new-question"), memoryId);
        assertEquals("ok", result);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(1)).chat(requestCaptor.capture());
        List<?> modelMessages = requestCaptor.getValue().messages();
        assertEquals(4, modelMessages.size(), "应只向模型发送裁剪后的历史 + 当前用户消息");

        String modelPayload = modelMessages.toString();
        assertTrue(modelPayload.contains("you are a test agent"));
        assertTrue(modelPayload.contains("old-3"));
        assertTrue(modelPayload.contains("old-4"));
        assertFalse(modelPayload.contains("old-1"), "较旧历史应被裁剪出模型输入窗口");
        assertFalse(modelPayload.contains("old-2"), "历史窗口应按上限裁剪");

        String persistedPayload = memoryProvider.get(memoryId).messages().toString();
        assertTrue(persistedPayload.contains("old-1"), "完整历史仍应保留在 ChatMemory");
    }

    @Test
    void shouldKeepCurrentTurnMessagesAcrossToolIterationsWhenHistoryIsTrimmed() {
        ChatModel chatModel = mock(ChatModel.class);
        InMemoryChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(200)
                .chatMemoryStore(chatMemoryStore)
                .build();

        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .name("echo")
                .arguments("{\"text\":\"hi\"}")
                .build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(toolCall)).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("done")).build());

        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .modelContextMaxMessages(2)
                .toolFromObject(new EchoTool())
                .build();

        String memoryId = "conv-model-history-limit-tool-loop";
        ChatMemory memory = memoryProvider.get(memoryId);
        memory.add(SystemMessage.from("you are a test agent"));
        memory.add(UserMessage.from("old-1"));
        memory.add(AiMessage.from("old-2"));
        memory.add(UserMessage.from("old-3"));
        memory.add(AiMessage.from("old-4"));

        String result = agent.execute(requestOf("new-question"), memoryId);
        assertEquals("done", result);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(2)).chat(requestCaptor.capture());
        List<ChatRequest> calls = requestCaptor.getAllValues();

        String firstCallPayload = calls.get(0).messages().toString();
        assertTrue(firstCallPayload.contains("new-question"), "首轮模型输入应包含当前用户问题");
        assertFalse(firstCallPayload.contains("old-1"), "被裁剪的旧历史不应进入首轮模型输入");

        String secondCallPayload = calls.get(1).messages().toString();
        assertTrue(secondCallPayload.contains("echo-result:hi"), "工具结果应进入下一轮模型输入");
        assertFalse(secondCallPayload.contains("old-1"), "被裁剪的旧历史不应在后续轮次重新进入模型输入");
    }

    @Test
    void shouldNotTrimModelContextWhenLimitIsZero() {
        ChatModel chatModel = mock(ChatModel.class);
        InMemoryChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(200)
                .chatMemoryStore(chatMemoryStore)
                .build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());

        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .modelContextMaxMessages(0)
                .build();

        String memoryId = "conv-model-context-unlimited";
        ChatMemory memory = memoryProvider.get(memoryId);
        memory.add(SystemMessage.from("you are a test agent"));
        memory.add(UserMessage.from("old-1"));
        memory.add(AiMessage.from("old-2"));
        memory.add(UserMessage.from("old-3"));
        memory.add(AiMessage.from("old-4"));

        String result = agent.execute(requestOf("new-question"), memoryId);
        assertEquals("ok", result);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(1)).chat(requestCaptor.capture());
        String modelPayload = requestCaptor.getValue().messages().toString();
        assertTrue(modelPayload.contains("old-1"));
        assertTrue(modelPayload.contains("old-2"));
        assertTrue(modelPayload.contains("old-3"));
        assertTrue(modelPayload.contains("old-4"));
    }

    @Test
    void shouldKeepOnlySystemAndCurrentUserWhenModelContextLimitIsOne() {
        ChatModel chatModel = mock(ChatModel.class);
        InMemoryChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(200)
                .chatMemoryStore(chatMemoryStore)
                .build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());

        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .modelContextMaxMessages(1)
                .build();

        String memoryId = "conv-model-context-one";
        ChatMemory memory = memoryProvider.get(memoryId);
        memory.add(SystemMessage.from("you are a test agent"));
        memory.add(UserMessage.from("old-1"));
        memory.add(AiMessage.from("old-2"));

        String result = agent.execute(requestOf("new-question"), memoryId);
        assertEquals("ok", result);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(1)).chat(requestCaptor.capture());
        List<?> modelMessages = requestCaptor.getValue().messages();
        assertEquals(2, modelMessages.size(), "max=1 时应保留 system + 当前 user");
        String modelPayload = modelMessages.toString();
        assertTrue(modelPayload.contains("you are a test agent"));
        assertTrue(modelPayload.contains("new-question"));
        assertFalse(modelPayload.contains("old-1"));
        assertFalse(modelPayload.contains("old-2"));
    }

    @Test
    void shouldLimitTotalModelMessagesAndKeepOrder() {
        ChatModel chatModel = mock(ChatModel.class);
        InMemoryChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(200)
                .chatMemoryStore(chatMemoryStore)
                .build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());

        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .modelContextMaxMessages(0)
                .modelContextMaxTotalMessages(3)
                .build();

        String memoryId = "conv-model-context-total-limit";
        ChatMemory memory = memoryProvider.get(memoryId);
        memory.add(SystemMessage.from("you are a test agent"));
        memory.add(UserMessage.from("old-1"));
        memory.add(AiMessage.from("old-2"));
        memory.add(UserMessage.from("old-3"));

        String result = agent.execute(requestOf("new-question"), memoryId);
        assertEquals("ok", result);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(1)).chat(requestCaptor.capture());
        List<ChatMessage> modelMessages = requestCaptor.getValue().messages();
        assertEquals(3, modelMessages.size(), "总窗口应限制模型输入消息条数");
        String payload = modelMessages.toString();
        assertTrue(payload.contains("you are a test agent"));
        assertTrue(payload.contains("old-3"));
        assertTrue(payload.contains("new-question"));
        assertFalse(payload.contains("old-1"));
        assertFalse(payload.contains("old-2"));
    }

    @Test
    void shouldKeepCurrentUserMessageWhenTotalModelLimitIsOne() {
        ChatModel chatModel = mock(ChatModel.class);
        InMemoryChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(200)
                .chatMemoryStore(chatMemoryStore)
                .build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());

        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .modelContextMaxMessages(0)
                .modelContextMaxTotalMessages(1)
                .build();

        String memoryId = "conv-model-context-total-one";
        ChatMemory memory = memoryProvider.get(memoryId);
        memory.add(SystemMessage.from("you are a test agent"));
        memory.add(UserMessage.from("old-1"));
        memory.add(AiMessage.from("old-2"));

        String result = agent.execute(requestOf("new-question"), memoryId);
        assertEquals("ok", result);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(1)).chat(requestCaptor.capture());
        List<ChatMessage> modelMessages = requestCaptor.getValue().messages();
        assertEquals(1, modelMessages.size(), "total=1 时模型输入应被限制为 1 条消息");
        String payload = modelMessages.toString();
        assertTrue(payload.contains("new-question"), "total=1 时必须保留当前用户问题");
        assertFalse(payload.contains("you are a test agent"), "total=1 时不保证保留 system message");
    }

    @Test
    void shouldLimitModelContextByApproxTokensAndKeepCriticalMessages() {
        ChatModel chatModel = mock(ChatModel.class);
        InMemoryChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(200)
                .chatMemoryStore(chatMemoryStore)
                .build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());

        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .modelContextMaxMessages(0)
                .modelContextMaxTotalMessages(0)
                .modelContextMaxApproxTokens(120)
                .build();

        String memoryId = "conv-model-context-approx-token-limit";
        ChatMemory memory = memoryProvider.get(memoryId);
        memory.add(SystemMessage.from("you are a test agent"));
        memory.add(UserMessage.from("old-large-1-" + "A".repeat(1000)));
        memory.add(AiMessage.from("old-large-2-" + "B".repeat(1000)));
        memory.add(UserMessage.from("recent-small"));

        String result = agent.execute(requestOf("new-question"), memoryId);
        assertEquals("ok", result);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(1)).chat(requestCaptor.capture());
        String payload = requestCaptor.getValue().messages().toString();

        assertTrue(payload.contains("new-question"), "token 预算下应保留当前用户消息");
        assertTrue(payload.contains("you are a test agent"), "token 预算下应尽量保留系统提示");
        assertTrue(payload.contains("recent-small"), "token 预算下应优先保留最近小消息");
        assertFalse(payload.contains("old-large-1-"), "超预算的大历史应被裁剪");
        assertFalse(payload.contains("old-large-2-"), "超预算的大历史应被裁剪");
    }

    @Test
    void shouldKeepSystemAndCurrentUserWhenTotalModelLimitIsTwo() {
        ChatModel chatModel = mock(ChatModel.class);
        InMemoryChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(200)
                .chatMemoryStore(chatMemoryStore)
                .build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());

        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .modelContextMaxMessages(0)
                .modelContextMaxTotalMessages(2)
                .build();

        String memoryId = "conv-model-context-total-two";
        ChatMemory memory = memoryProvider.get(memoryId);
        memory.add(SystemMessage.from("you are a test agent"));
        memory.add(UserMessage.from("old-1"));
        memory.add(AiMessage.from("old-2"));

        String result = agent.execute(requestOf("new-question"), memoryId);
        assertEquals("ok", result);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(1)).chat(requestCaptor.capture());
        List<ChatMessage> modelMessages = requestCaptor.getValue().messages();
        assertEquals(2, modelMessages.size(), "total=2 时应保留 system + 当前用户消息");
        String payload = modelMessages.toString();
        assertTrue(payload.contains("you are a test agent"));
        assertTrue(payload.contains("new-question"));
        assertFalse(payload.contains("old-1"));
        assertFalse(payload.contains("old-2"));
    }

    @Test
    void shouldKeepCurrentUserAndLatestToolResultWhenTotalModelLimitIsTwoInToolLoop() {
        ChatModel chatModel = mock(ChatModel.class);
        InMemoryChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(200)
                .chatMemoryStore(chatMemoryStore)
                .build();
        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .name("echo")
                .arguments("{\"text\":\"hello\"}")
                .build();

        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(toolCall)).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("done")).build());

        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .modelContextMaxMessages(2)
                .modelContextMaxTotalMessages(2)
                .toolFromObject(new EchoTool())
                .build();

        String memoryId = "conv-model-context-total-two-tool-loop";
        ChatMemory memory = memoryProvider.get(memoryId);
        memory.add(SystemMessage.from("you are a test agent"));
        memory.add(UserMessage.from("old-1"));
        memory.add(AiMessage.from("old-2"));

        String result = agent.execute(requestOf("new-question"), memoryId);
        assertEquals("done", result);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(2)).chat(requestCaptor.capture());
        ChatRequest secondCall = requestCaptor.getAllValues().get(1);
        assertTrue(secondCall.messages().size() <= 2, "后续轮次应持续受 total=2 限制");
        String secondCallPayload = secondCall.messages().toString();
        assertTrue(secondCallPayload.contains("new-question"), "后续轮次应保留当前用户问题");
        assertTrue(secondCallPayload.contains("echo-result:hello"), "后续轮次应保留最新工具结果");
    }

    @Test
    void shouldTrimModelContextOnEveryReactIteration() {
        ChatModel chatModel = mock(ChatModel.class);
        InMemoryChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(200)
                .chatMemoryStore(chatMemoryStore)
                .build();
        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .name("echo")
                .arguments("{\"text\":\"hello\"}")
                .build();

        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(toolCall)).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("done")).build());

        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .modelContextMaxMessages(2)
                .modelContextMaxTotalMessages(4)
                .toolFromObject(new EchoTool())
                .build();

        String memoryId = "conv-model-context-react";
        ChatMemory memory = memoryProvider.get(memoryId);
        memory.add(SystemMessage.from("you are a test agent"));
        memory.add(UserMessage.from("old-1"));
        memory.add(AiMessage.from("old-2"));
        memory.add(UserMessage.from("old-3"));

        String result = agent.execute(requestOf("new-question"), memoryId);
        assertEquals("done", result);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(2)).chat(requestCaptor.capture());
        List<ChatRequest> calls = requestCaptor.getAllValues();

        assertEquals(3, calls.get(0).messages().size(), "首轮应是裁剪后历史 + 当前 user");
        String secondCallPayload = calls.get(1).messages().toString();
        assertTrue(calls.get(1).messages().size() <= 4, "后续轮次应受总窗口上限约束");
        assertTrue(secondCallPayload.contains("echo-result:hello"), "后续轮次应包含本轮工具执行结果");
        assertFalse(secondCallPayload.contains("old-1"), "被裁剪的旧历史不应回流到后续轮次");
    }

    @Test
    void shouldKeepLatestToolResultWhenApproxBudgetIsTinyInToolLoop() {
        ChatModel chatModel = mock(ChatModel.class);
        InMemoryChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(200)
                .chatMemoryStore(chatMemoryStore)
                .build();
        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .name("echo")
                .arguments("{\"text\":\"hello\"}")
                .build();

        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(toolCall)).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("done")).build());

        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .modelContextMaxMessages(0)
                .modelContextMaxTotalMessages(0)
                .modelContextMaxApproxTokens(25)
                .toolFromObject(new EchoTool())
                .build();

        String memoryId = "conv-approx-budget-tiny-tool-loop";
        ChatMemory memory = memoryProvider.get(memoryId);
        memory.add(SystemMessage.from("you are a test agent " + "X".repeat(200)));
        memory.add(UserMessage.from("old-1"));
        memory.add(AiMessage.from("old-2"));

        String result = agent.execute(requestOf("new-question"), memoryId);
        assertEquals("done", result);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(2)).chat(requestCaptor.capture());
        String secondCallPayload = requestCaptor.getAllValues().get(1).messages().toString();
        assertTrue(secondCallPayload.contains("new-question"), "极小预算下仍应保留当前用户问题");
        assertTrue(secondCallPayload.contains("echo-result:hello"), "极小预算下应优先保留最新工具结果以保障循环收敛");
    }

    @Test
    void shouldContinueToolLoopBeyondTenIterationsWhenMaxIterationsIsUnlimited() {
        ChatModel chatModel = mock(ChatModel.class);
        InMemoryChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(400)
                .chatMemoryStore(chatMemoryStore)
                .build();

        AtomicInteger callCount = new AtomicInteger(0);
        when(chatModel.chat(any(ChatRequest.class))).thenAnswer(invocation -> {
            int current = callCount.incrementAndGet();
            if (current <= 11) {
                ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                        .name("echo")
                        .arguments("{\"text\":\"round-" + current + "\"}")
                        .build();
                return ChatResponse.builder().aiMessage(AiMessage.from(toolCall)).build();
            }
            return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
        });

        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .maxIterations(0)
                .toolFromObject(new EchoTool())
                .build();

        String result = agent.execute(requestOf("loop please"), "conv-unlimited-iterations");
        assertEquals("done", result);

        verify(chatModel, times(12)).chat(any(ChatRequest.class));
    }

    @Test
    void shouldAbortWhenIdenticalToolBatchRepeatsOverThreshold() {
        ChatModel chatModel = mock(ChatModel.class);
        InMemoryChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(200)
                .chatMemoryStore(chatMemoryStore)
                .build();

        ToolExecutionRequest repeatedToolCall = ToolExecutionRequest.builder()
                .name("echo")
                .arguments("{\"text\":\"same\"}")
                .build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(repeatedToolCall)).build());

        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .maxIterations(0)
                .repeatedToolCallThreshold(3)
                .toolFromObject(new EchoTool())
                .build();

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> agent.execute(requestOf("repeat loop"), "conv-repeat-threshold"));
        assertTrue(ex.getMessage().contains("repeated identical tool-call batch"));
        verify(chatModel, times(4)).chat(any(ChatRequest.class));
    }

    @Test
    void shouldCapTotalModelMessagesPerRoundWhenConfigured() {
        ChatModel chatModel = mock(ChatModel.class);
        InMemoryChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(200)
                .chatMemoryStore(chatMemoryStore)
                .build();
        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .name("echo")
                .arguments("{\"text\":\"hello\"}")
                .build();

        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(toolCall)).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("done")).build());

        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .modelContextMaxMessages(2)
                .modelContextMaxTotalMessages(3)
                .toolFromObject(new EchoTool())
                .build();

        String memoryId = "conv-model-total-limit";
        ChatMemory memory = memoryProvider.get(memoryId);
        memory.add(SystemMessage.from("you are a test agent"));
        memory.add(UserMessage.from("old-1"));
        memory.add(AiMessage.from("old-2"));
        memory.add(UserMessage.from("old-3"));

        String result = agent.execute(requestOf("new-question"), memoryId);
        assertEquals("done", result);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(2)).chat(requestCaptor.capture());
        List<ChatRequest> calls = requestCaptor.getAllValues();

        assertTrue(calls.get(0).messages().size() <= 3, "首轮模型输入应受 total 上限约束");
        assertTrue(calls.get(1).messages().size() <= 3, "后续轮次模型输入应受 total 上限约束");
        String secondCallPayload = calls.get(1).messages().toString();
        assertTrue(secondCallPayload.contains("echo-result:hello"), "total 裁剪不应丢失本轮关键工具结果");
        assertFalse(secondCallPayload.contains("old-1"), "旧历史不应在 total 裁剪后回流");
    }

    @Test
    void shouldKeepCompactedToolResultVisibleInNextTurnUnderTotalLimit() {
        ChatModel chatModel = mock(ChatModel.class);
        InMemoryChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(200)
                .chatMemoryStore(chatMemoryStore)
                .build();

        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .name("dumpLarge")
                .arguments("{}")
                .build();

        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(toolCall)).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("first-done")).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("second-done")).build());

        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .enableToolResultCompaction(true)
                .memoryToolResultMaxChars(256)
                .compactToolResultHeadChars(64)
                .compactToolResultTailChars(32)
                .modelContextMaxMessages(0)
                .modelContextMaxTotalMessages(4)
                .toolFromObject(new LargeOutputTool())
                .build();

        String memoryId = "conv-compact-total-limit";
        assertEquals("first-done", agent.execute(requestOf("first-question"), memoryId));
        assertEquals("second-done", agent.execute(requestOf("second-question"), memoryId));

        ChatMemory memory = memoryProvider.get(memoryId);
        String persistedPayload = memory.messages().toString();
        assertTrue(persistedPayload.contains("[Tool Result Compacted]"),
                "压缩开启后，工具结果应以压缩形态持久化到 ChatMemory");

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(3)).chat(requestCaptor.capture());
        ChatRequest thirdCall = requestCaptor.getAllValues().get(2);

        assertTrue(thirdCall.messages().size() <= 4, "下一轮模型输入应持续受 total 上限约束");
        String modelPayload = thirdCall.messages().toString();
        assertTrue(modelPayload.contains("second-question"), "下一轮应包含当前用户问题");
        assertTrue(modelPayload.contains("[Tool Result Compacted]"), "下一轮应能读取到上一轮压缩后的工具结果");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldApplyTotalLimitWhenCurrentUserIdentityCannotBeFound() throws Exception {
        ChatModel chatModel = mock(ChatModel.class);
        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .modelContextMaxMessages(0)
                .modelContextMaxTotalMessages(3)
                .build();

        List<ChatMessage> fullMessages = new ArrayList<>();
        fullMessages.add(SystemMessage.from("you are a test agent"));
        fullMessages.add(UserMessage.from("old-1"));
        fullMessages.add(AiMessage.from("old-2"));
        fullMessages.add(UserMessage.from("old-3"));
        UserMessage detachedCurrentUser = UserMessage.from("new-question");

        Method method = AbstractAgentExecutor.class
                .getDeclaredMethod("buildModelMessages", List.class, UserMessage.class);
        method.setAccessible(true);

        List<ChatMessage> modelMessages =
                (List<ChatMessage>) method.invoke(agent, fullMessages, detachedCurrentUser);

        assertTrue(modelMessages.size() <= 3, "currentUserIndex<0 分支也应受 total 上限约束");
        String payload = modelMessages.toString();
        assertTrue(payload.contains("new-question"), "identity 未命中时也应保留当前用户问题");
        assertTrue(payload.contains("old-3"), "应保留最近历史消息");
        assertFalse(payload.contains("old-1"), "较旧历史应被 total 裁剪");
        assertFalse(payload.contains("old-2"), "total 裁剪应优先淘汰更旧的历史消息");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldHandleDetachedCurrentUserWhenHistoryTrimReturnsImmutableList() throws Exception {
        ChatModel chatModel = mock(ChatModel.class);
        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .modelContextMaxMessages(1)
                .modelContextMaxTotalMessages(3)
                .build();

        List<ChatMessage> fullMessages = List.of(
                SystemMessage.from("you are a test agent"),
                UserMessage.from("old-1"),
                AiMessage.from("old-2")
        );
        UserMessage detachedCurrentUser = UserMessage.from("new-question");

        Method method = AbstractAgentExecutor.class
                .getDeclaredMethod("buildModelMessages", List.class, UserMessage.class);
        method.setAccessible(true);

        List<ChatMessage> modelMessages =
                (List<ChatMessage>) method.invoke(agent, fullMessages, detachedCurrentUser);

        assertTrue(modelMessages.size() <= 3, "不可变历史分支也应受 total 上限约束");
        String payload = modelMessages.toString();
        assertTrue(payload.contains("new-question"), "应保留当前用户问题");
        assertTrue(payload.contains("you are a test agent"), "history=1 时应保留 system message");
    }

    @Test
    void shouldExecuteWithReadOnlyMemoryMessagesView() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("done-1")).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("done-2")).build());

        Map<Object, ChatMemory> memories = new ConcurrentHashMap<>();
        ChatMemoryProvider memoryProvider = memoryId ->
                memories.computeIfAbsent(memoryId, ReadOnlyViewChatMemory::new);

        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .build();

        String memoryId = "conv-readonly-memory-view";
        assertEquals("done-1", agent.execute(requestOf("first"), memoryId));
        assertEquals("done-2", agent.execute(requestOf("second"), memoryId));

        ChatMemory memory = memoryProvider.get(memoryId);
        assertTrue(memory.messages().size() >= 5, "memory 应持续累积跨轮消息");

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(2)).chat(requestCaptor.capture());
        String secondCallPayload = requestCaptor.getAllValues().get(1).messages().toString();
        assertTrue(secondCallPayload.contains("first"), "第二轮模型输入应包含上一轮用户消息");
    }

    @Test
    void shouldPersistSystemMessageOnlyOnceOnConcurrentFirstTurns() throws Exception {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());
        var dir = Files.createTempDirectory("agent-system-message-once-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);
        ChatMemoryProvider memoryProvider = memoryId -> new PersistentChatMemory(memoryId, store);

        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
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
                    agent.execute(requestOf("q-" + idx), "conv-system-once");
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

        ChatMemory memory = memoryProvider.get("conv-system-once");
        long systemCount = memory.messages().stream().filter(message -> message instanceof SystemMessage).count();
        assertEquals(1, systemCount, "同一会话的 SystemMessage 应只写入一次");
    }

    @Test
    void shouldPersistSystemMessageOnlyOnceOnConcurrentFirstTurnsForMessageWindowMemory() throws Exception {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());

        InMemoryChatMemoryStore store = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(100)
                .chatMemoryStore(store)
                .build();

        TestAgent agent = TestAgent.builder()
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
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
                    agent.execute(requestOf("qmw-" + idx), "conv-system-once-message-window");
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

        ChatMemory memory = memoryProvider.get("conv-system-once-message-window");
        long systemCount = memory.messages().stream().filter(message -> message instanceof SystemMessage).count();
        assertEquals(1, systemCount, "MessageWindowChatMemory 分支同一会话 SystemMessage 应只写入一次");
    }

    private static ToolExecutionRequest requestOf(String context) {
        return ToolExecutionRequest.builder()
                .name("test_agent")
                .arguments(context)
                .build();
    }

    private static ToolExecutionRequest legacyRequestOf(String context) {
        return ToolExecutionRequest.builder()
                .name("test_agent")
                .arguments("{\"context\":\"" + context + "\"}")
                .build();
    }

    static class EchoTool {
        @Tool("回显工具")
        public String echo(@P("文本") String text) {
            return "echo-result:" + text;
        }
    }

    static class LargeOutputTool {
        @Tool("生成大文本")
        public String dumpLarge() {
            return "L".repeat(3000);
        }
    }

    static class DomainLargeOutputTool {
        @Tool("生成 python 大文本")
        public String dumpPythonLarge() {
            return "PYTHON_RESULT_" + "P".repeat(3000);
        }

        @Tool("生成 browser 大文本")
        public String dumpBrowserLarge() {
            return "BROWSER_RESULT_" + "B".repeat(3000);
        }
    }

    static class InMemoryArtifactStore implements ToolResultArtifactStore {
        private final Map<String, String> artifacts = new ConcurrentHashMap<>();

        @Override
        public String save(Object memoryId, String toolName, String toolArguments, String outcome) {
            String hash = Integer.toHexString(outcome.hashCode());
            String padded = "0".repeat(Math.max(0, 64 - hash.length())) + hash;
            String id = "sha256:" + padded;
            artifacts.putIfAbsent(id, outcome);
            return id;
        }

        @Override
        public Optional<String> load(String artifactId) {
            return Optional.ofNullable(artifacts.get(artifactId));
        }

        int size() {
            return artifacts.size();
        }

        String firstPayload() {
            return artifacts.values().stream().findFirst().orElse("");
        }
    }

    private static int countOccurrences(String text, String token) {
        if (text == null || text.isEmpty() || token == null || token.isEmpty()) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while (true) {
            idx = text.indexOf(token, idx);
            if (idx < 0) {
                return count;
            }
            count++;
            idx += token.length();
        }
    }

    private static String extractBlock(String text, String beginMarker, String endMarker) {
        int begin = text.indexOf(beginMarker);
        if (begin < 0) {
            return "";
        }
        int from = begin + beginMarker.length();
        int end = text.indexOf(endMarker, from);
        if (end < 0) {
            return "";
        }
        return text.substring(from, end);
    }

    private static String extractTailBlock(String text, String beginMarker) {
        int begin = text.indexOf(beginMarker);
        if (begin < 0) {
            return "";
        }
        int from = begin + beginMarker.length();
        return text.substring(from).stripTrailing();
    }

    static class TestAgent extends AbstractAgentExecutor<TestAgent.Builder> {

        static class Builder extends AbstractAgentExecutor.Builder<Builder> {
            public TestAgent build() {
                this.name("test_agent")
                        .description("test agent")
                        .singleParameter("input")
                        .systemMessage(SystemMessage.from("you are a test agent"));
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

    static class ReadOnlyViewChatMemory implements ChatMemory {
        private final Object id;
        private final List<ChatMessage> messages = new ArrayList<>();

        ReadOnlyViewChatMemory(Object id) {
            this.id = id;
        }

        @Override
        public Object id() {
            return id;
        }

        @Override
        public void add(ChatMessage chatMessage) {
            messages.add(chatMessage);
        }

        @Override
        public List<ChatMessage> messages() {
            return Collections.unmodifiableList(new ArrayList<>(messages));
        }

        @Override
        public void clear() {
            messages.clear();
        }
    }
}
