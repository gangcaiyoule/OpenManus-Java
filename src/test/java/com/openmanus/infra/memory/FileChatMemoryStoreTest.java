package com.openmanus.infra.memory;

import com.openmanus.aiframework.runtime.model.AiChatMessage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileChatMemoryStoreTest {

    @Test
    void shouldPersistMessagesToDisk() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-");
        FileChatMemoryStore firstStore = new FileChatMemoryStore(dir);

        firstStore.updateMessages("conv-1", List.of(AiChatMessage.user("hello")));

        FileChatMemoryStore secondStore = new FileChatMemoryStore(dir);
        List<AiChatMessage> restored = secondStore.getMessages("conv-1");

        assertEquals(1, restored.size());
        assertEquals("hello", restored.getFirst().content());
    }

    @Test
    void shouldReadRuntimeWrappedMessagesFormat() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-runtime-wrapped-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);
        String hashed = sha256("conv-runtime-wrap");
        Path file = dir.resolve(hashed + ".json");
        Files.writeString(file, "{\"messages\":[{\"role\":\"USER\",\"content\":\"runtime\"}]}", StandardCharsets.UTF_8);

        List<AiChatMessage> restored = store.getMessages("conv-runtime-wrap");
        assertEquals(1, restored.size());
        assertEquals(AiChatMessage.Role.USER, restored.getFirst().role());
        assertEquals("runtime", restored.getFirst().content());
    }

    @Test
    void shouldIgnoreNullItemsInRuntimeMessagesArray() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-runtime-null-items-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);
        String hashed = sha256("conv-runtime-null-items");
        Path file = dir.resolve(hashed + ".json");
        Files.writeString(file, """
                {"messages":[
                  {"role":"USER","content":"runtime-user"},
                  null,
                  {"role":"SYSTEM","content":"runtime-system"}
                ]}
                """, StandardCharsets.UTF_8);

        List<AiChatMessage> restored = store.getMessages("conv-runtime-null-items");
        assertEquals(2, restored.size());
        assertEquals(AiChatMessage.Role.USER, restored.get(0).role());
        assertEquals("runtime-user", restored.get(0).content());
        assertEquals(AiChatMessage.Role.SYSTEM, restored.get(1).role());
        assertEquals("runtime-system", restored.get(1).content());
    }

    @Test
    void constructorShouldFailWhenBaseDirIsAFile() throws Exception {
        Path baseFile = Files.createTempFile("file-chat-memory-invalid-base-", ".txt");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new FileChatMemoryStore(baseFile));
        assertTrue(ex.getMessage().contains("Failed to initialize chat memory dir"));
    }

    @Test
    void shouldReturnEmptyWhenStoredJsonIsBlank() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-blank-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);
        String hashed = sha256("conv-blank");
        Path file = dir.resolve(hashed + ".json");
        Files.writeString(file, "   \n\t", StandardCharsets.UTF_8);

        assertTrue(store.getMessages("conv-blank").isEmpty());
    }

    @Test
    void shouldReadLegacyLangChainJsonFormat() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-legacy-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);

        String hashed = sha256("conv-legacy");
        Path file = dir.resolve(hashed + ".json");
        Files.writeString(file, """
                [
                  {"type":"USER","text":"legacy"}
                ]
                """, StandardCharsets.UTF_8);

        List<AiChatMessage> restored = store.getMessages("conv-legacy");
        assertEquals(1, restored.size());
        assertEquals(AiChatMessage.Role.USER, restored.getFirst().role());
        assertEquals("legacy", restored.getFirst().content());
    }

    @Test
    void shouldReadLegacyAiMessageWithToolCalls() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-legacy-ai-tools-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);

        String hashed = sha256("conv-legacy-ai-tools");
        Path file = dir.resolve(hashed + ".json");
        Files.writeString(file, """
                [
                  {
                    "type":"AI",
                    "text":"assistant",
                    "toolExecutionRequests":[
                      {"id":"call-1","name":"search","arguments":{"q":"today"}},
                      {"name":"summarize","arguments":"{\\"topic\\":\\"x\\"}"},
                      {"id":"call-2","name":"default-args"},
                      {"name":"","arguments":{"bad":true}}
                    ]
                  }
                ]
                """, StandardCharsets.UTF_8);

        List<AiChatMessage> restored = store.getMessages("conv-legacy-ai-tools");
        assertEquals(1, restored.size());
        AiChatMessage message = restored.getFirst();
        assertEquals(AiChatMessage.Role.ASSISTANT, message.role());
        assertEquals("assistant", message.content());
        assertEquals(3, message.toolCalls().size());
        assertEquals("search", message.toolCalls().get(0).name());
        assertEquals("{\"q\":\"today\"}", message.toolCalls().get(0).arguments());
        assertEquals("summarize", message.toolCalls().get(1).name());
        assertEquals("default-args", message.toolCalls().get(2).name());
        assertEquals("{}", message.toolCalls().get(2).arguments());
    }

    @Test
    void shouldReadLegacySystemMessageWithContentsPayload() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-legacy-system-contents-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);

        String hashed = sha256("conv-legacy-system-contents");
        Path file = dir.resolve(hashed + ".json");
        Files.writeString(file, """
                [
                  {"type":"SYSTEM","contents":[{"text":"sys-"},{"text":"rule"}]}
                ]
                """, StandardCharsets.UTF_8);

        List<AiChatMessage> restored = store.getMessages("conv-legacy-system-contents");
        assertEquals(1, restored.size());
        assertEquals(AiChatMessage.Role.SYSTEM, restored.getFirst().role());
        assertEquals("sys-rule", restored.getFirst().content());
    }

    @Test
    void shouldReadLegacyToolExecutionResultMessage() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-legacy-tool-result-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);

        String hashed = sha256("conv-legacy-tool-result");
        Path file = dir.resolve(hashed + ".json");
        Files.writeString(file, """
                [
                  {"type":"TOOL_EXECUTION_RESULT","toolExecutionRequestId":"tool-call-1","toolName":"search","text":"ok"},
                  {"type":"TOOL_EXECUTION_RESULT","name":"fetch","id":"tool-call-2","text":"done"},
                  {"type":"TOOL_EXECUTION_RESULT","id":"tool-call-3","text":"skip"},
                  {"type":"TOOL_EXECUTION_RESULT","toolName":"missing-id","text":"skip"}
                ]
                """, StandardCharsets.UTF_8);

        List<AiChatMessage> restored = store.getMessages("conv-legacy-tool-result");
        assertEquals(3, restored.size());
        assertEquals(AiChatMessage.Role.TOOL, restored.get(0).role());
        assertEquals("search", restored.get(0).name());
        assertEquals("tool-call-1", restored.get(0).toolCallId());
        assertEquals("done", restored.get(1).content());
        assertEquals("missing-id", restored.get(2).name());
        assertFalse(restored.get(2).toolCallId().isBlank());
    }

    @Test
    void shouldIgnoreLegacyToolExecutionResultWithBlankToolIdentity() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-legacy-tool-result-blank-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);

        String hashed = sha256("conv-legacy-tool-result-blank");
        Path file = dir.resolve(hashed + ".json");
        Files.writeString(file, """
                [
                  {"type":"TOOL_EXECUTION_RESULT","toolExecutionRequestId":"tool-call-1","toolName":"search","text":"ok"},
                  {"type":"TOOL_EXECUTION_RESULT","id":"blank-name","toolName":"   ","text":"ignored"},
                  {"type":"TOOL_EXECUTION_RESULT","id":"   ","toolName":"fetch","text":"ignored"}
                ]
                """, StandardCharsets.UTF_8);

        List<AiChatMessage> restored = store.getMessages("conv-legacy-tool-result-blank");
        assertEquals(1, restored.size());
        assertEquals("search", restored.getFirst().name());
        assertEquals("tool-call-1", restored.getFirst().toolCallId());
    }

    @Test
    void shouldSkipLegacyNullAndTypeMissingEntries() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-legacy-skip-empty-type-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);

        String hashed = sha256("conv-legacy-skip-empty-type");
        Path file = dir.resolve(hashed + ".json");
        Files.writeString(file, """
                [
                  null,
                  {"text":"ignored"},
                  {"type":"CUSTOM","text":"ignored"},
                  {"type":"SYSTEM","text":"sys"}
                ]
                """, StandardCharsets.UTF_8);

        List<AiChatMessage> restored = store.getMessages("conv-legacy-skip-empty-type");
        assertEquals(1, restored.size());
        assertEquals(AiChatMessage.Role.SYSTEM, restored.getFirst().role());
    }

    @Test
    void shouldTreatNonArrayLegacyToolCallsAsEmpty() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-legacy-toolcalls-nonarray-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);

        String hashed = sha256("conv-legacy-toolcalls-nonarray");
        Path file = dir.resolve(hashed + ".json");
        Files.writeString(file, """
                [
                  {"type":"AI","text":"assistant","toolExecutionRequests":{"name":"x"}}
                ]
                """, StandardCharsets.UTF_8);

        List<AiChatMessage> restored = store.getMessages("conv-legacy-toolcalls-nonarray");
        assertEquals(1, restored.size());
        assertTrue(restored.getFirst().toolCalls().isEmpty());
    }

    @Test
    void shouldSkipInvalidLegacyToolCallItemsAndAcceptNullFieldShapes() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-legacy-toolcalls-invalid-items-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);

        String hashed = sha256("conv-legacy-toolcalls-invalid-items");
        Path file = dir.resolve(hashed + ".json");
        Files.writeString(file, """
                [
                  {"type":"AI","text":"assistant","toolExecutionRequests":[
                    1,
                    {"id":"x"},
                    {"name":"   ","arguments":{}},
                    {"name":null,"arguments":{}},
                    {"name":"ok","arguments":null}
                  ]},
                  {"type":"TOOL_EXECUTION_RESULT","id":null,"toolName":"t","text":"ignored"}
                ]
                """, StandardCharsets.UTF_8);

        List<AiChatMessage> restored = store.getMessages("conv-legacy-toolcalls-invalid-items");
        assertEquals(1, restored.size());
        assertEquals(1, restored.getFirst().toolCalls().size());
        assertEquals("ok", restored.getFirst().toolCalls().getFirst().name());
        assertEquals("null", restored.getFirst().toolCalls().getFirst().arguments());
    }

    @Test
    void shouldExtractLegacyTextFromContentsAndIgnoreInvalidSegments() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-legacy-contents-shapes-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);

        String hashed = sha256("conv-legacy-contents-shapes");
        Path file = dir.resolve(hashed + ".json");
        Files.writeString(file, """
                [
                  {"type":"SYSTEM","contents":[1,{"x":1},{"text":null},{"text":"ok"}]},
                  {"type":"USER","contents":{"text":"not-array"}}
                ]
                """, StandardCharsets.UTF_8);

        List<AiChatMessage> restored = store.getMessages("conv-legacy-contents-shapes");
        assertEquals(2, restored.size());
        assertEquals("ok", restored.get(0).content());
        assertEquals("", restored.get(1).content());
    }

    @Test
    void shouldHandleLegacyNullTextAndNonArrayContents() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-legacy-null-text-non-array-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);

        String hashed = sha256("conv-legacy-null-text-non-array");
        Path file = dir.resolve(hashed + ".json");
        Files.writeString(file, """
                [
                  {"type":"USER","text":null,"contents":{"text":"fallback"}}
                ]
                """, StandardCharsets.UTF_8);

        List<AiChatMessage> restored = store.getMessages("conv-legacy-null-text-non-array");
        assertEquals(1, restored.size());
        assertEquals("", restored.getFirst().content());
    }

    @Test
    void shouldDeleteMessages() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-delete-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);
        store.updateMessages("conv-2", List.of(AiChatMessage.user("bye")));
        store.getMessages("conv-2");

        String hashed = sha256("conv-2");
        Path lockFile = dir.resolve(hashed + ".lck");
        assertTrue(Files.exists(lockFile), "访问后应生成锁文件");

        store.deleteMessages("conv-2");

        assertTrue(Files.notExists(lockFile), "删除会话时应同步清理锁文件");
        assertTrue(store.getMessages("conv-2").isEmpty());
    }

    @Test
    void shouldPersistEmptyListWhenUpdatingWithNullMessages() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-null-messages-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);
        store.updateMessages("conv-null-list", null);
        assertTrue(store.getMessages("conv-null-list").isEmpty());
    }

    @Test
    void shouldHandleMissingBaseDirForReadWriteAndDeleteGracefully() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-missing-dir-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);
        Files.deleteIfExists(dir.resolve("corrupted"));
        Files.delete(dir);

        assertThrows(IllegalStateException.class, () -> store.getMessages("conv-missing-read"));
        assertThrows(IllegalStateException.class, () -> store.updateMessages("conv-missing-write", List.of(AiChatMessage.user("x"))));
        assertThrows(IllegalStateException.class, () -> store.append("conv-missing-append", AiChatMessage.user("x")));
        assertThrows(IllegalStateException.class, () -> store.appendIfAbsent("conv-missing-append-if-absent",
                AiChatMessage.user("x"), message -> false));
        store.deleteMessages("conv-missing-delete");
    }

    @Test
    void shouldAppendSystemMessageIfAbsentOnlyOnce() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-system-append-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);

        assertTrue(store.appendSystemMessageIfAbsent("conv-sys", "sys"));
        assertFalse(store.appendSystemMessageIfAbsent("conv-sys", "sys"));

        List<AiChatMessage> messages = store.getMessages("conv-sys");
        assertEquals(1, messages.size());
        assertEquals(AiChatMessage.Role.SYSTEM, messages.getFirst().role());
    }

    @Test
    void shouldRejectBlankSystemMessageWhenAppendingSystemMessageIfAbsent() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-system-blank-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);

        assertFalse(store.appendSystemMessageIfAbsent("conv-sys-blank", null));
        assertFalse(store.appendSystemMessageIfAbsent("conv-sys-blank", ""));
        assertFalse(store.appendSystemMessageIfAbsent("conv-sys-blank", "   "));
        assertTrue(store.getMessages("conv-sys-blank").isEmpty());
    }

    @Test
    void shouldIgnoreNullMessageWhenCheckingSystemMessageAbsent() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-null-message-item-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);
        store.updateMessages("conv-null-item", Arrays.asList((AiChatMessage) null));

        assertTrue(store.appendSystemMessageIfAbsent("conv-null-item", "sys"));
        List<AiChatMessage> messages = store.getMessages("conv-null-item");
        assertEquals(1, messages.size());
        assertEquals(AiChatMessage.Role.SYSTEM, messages.getFirst().role());
    }

    @Test
    void shouldAppendConcurrentlyWithoutLosingMessages() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-concurrency-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);
        PersistentAiMemory memory = new PersistentAiMemory("conv-c", store);

        int tasks = 40;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(tasks);

        for (int i = 0; i < tasks; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    start.await(30, TimeUnit.SECONDS);
                    memory.add(AiChatMessage.user("msg-" + idx));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        executor.shutdownNow();

        List<AiChatMessage> messages = new ArrayList<>(memory.messages());
        assertEquals(tasks, messages.size(), "并发 append 不应丢消息");
    }

    @Test
    void shouldAppendConcurrentlyAcrossStoreInstancesWithoutLosingMessages() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-multi-store-");
        FileChatMemoryStore storeA = new FileChatMemoryStore(dir);
        FileChatMemoryStore storeB = new FileChatMemoryStore(dir);
        PersistentAiMemory memoryA = new PersistentAiMemory("conv-shared", storeA);
        PersistentAiMemory memoryB = new PersistentAiMemory("conv-shared", storeB);

        int tasks = 80;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(tasks);

        for (int i = 0; i < tasks; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    start.await(30, TimeUnit.SECONDS);
                    if (idx % 2 == 0) {
                        memoryA.add(AiChatMessage.user("a-" + idx));
                    } else {
                        memoryB.add(AiChatMessage.user("b-" + idx));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        executor.shutdownNow();

        List<AiChatMessage> messages = new ArrayList<>(memoryA.messages());
        assertEquals(tasks, messages.size(), "跨 store 实例并发 append 不应丢消息");
    }

    @Test
    void shouldFailFastWhenStoredJsonIsCorrupted() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-corrupt-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);
        store.updateMessages("conv-bad", List.of(AiChatMessage.user("ok")));

        String hashed = sha256("conv-bad");
        Path file = dir.resolve(hashed + ".json");
        Files.writeString(file, "{not-json", StandardCharsets.UTF_8);

        assertThrows(IllegalStateException.class, () -> store.getMessages("conv-bad"));
        assertTrue(Files.notExists(file), "损坏文件应被隔离而非继续留在主目录");
        Path corruptedDir = dir.resolve("corrupted");
        assertTrue(Files.isDirectory(corruptedDir), "应创建损坏文件隔离目录");
        long quarantinedCount;
        try (var stream = Files.list(corruptedDir)) {
            quarantinedCount = stream
                    .filter(path -> path.getFileName().toString().startsWith(hashed + ".json.corrupted-"))
                    .count();
        }
        assertEquals(1, quarantinedCount, "损坏文件应被移动到隔离目录");
    }

    @Test
    void shouldFailWithoutQuarantineWhenDisabled() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-corrupt-no-quarantine-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir, 30, false);
        store.updateMessages("conv-bad-no-quarantine", List.of(AiChatMessage.user("ok")));

        String hashed = sha256("conv-bad-no-quarantine");
        Path file = dir.resolve(hashed + ".json");
        Files.writeString(file, "{bad", StandardCharsets.UTF_8);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> store.getMessages("conv-bad-no-quarantine"));
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(Files.exists(file), "禁用隔离时损坏文件应保留在原路径");
    }

    @Test
    void shouldFailForUnsupportedJsonShapeAndQuarantine() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-unsupported-shape-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir, 30, true);
        String hashed = sha256("conv-unsupported");
        Path file = dir.resolve(hashed + ".json");
        Files.writeString(file, "{\"x\":1}", StandardCharsets.UTF_8);

        assertThrows(IllegalStateException.class, () -> store.getMessages("conv-unsupported"));
        assertTrue(Files.notExists(file));
    }

    @Test
    void shouldRejectNonObjectMessageNodeInRuntimeJson() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-invalid-node-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir, 30, true);
        String hashed = sha256("conv-invalid-node");
        Path file = dir.resolve(hashed + ".json");
        Files.writeString(file, "[1,{\"role\":\"USER\",\"content\":\"x\"}]", StandardCharsets.UTF_8);

        assertThrows(IllegalStateException.class, () -> store.getMessages("conv-invalid-node"));
    }

    @Test
    void shouldRejectRuntimeLikeNodeWithoutTypeInLegacyFallback() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-runtime-like-no-type-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir, 30, true);
        String hashed = sha256("conv-runtime-like-no-type");
        Path file = dir.resolve(hashed + ".json");
        Files.writeString(file, "[{\"content\":\"corrupted-runtime-shape\"}]", StandardCharsets.UTF_8);

        assertThrows(IllegalStateException.class, () -> store.getMessages("conv-runtime-like-no-type"));
        assertTrue(Files.notExists(file), "runtime-like 损坏文件应被隔离而不是被静默忽略");
    }

    @Test
    void shouldKeepRecentOrphanLockFilesOnStartup() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-recent-orphan-lock-");
        String hashed = sha256("conv-orphan-recent");
        Path lockFile = dir.resolve(hashed + ".lck");
        Files.writeString(lockFile, "", StandardCharsets.UTF_8);
        FileTime recentTime = FileTime.from(Instant.now().minus(Duration.ofHours(1)));
        Files.setLastModifiedTime(lockFile, recentTime);

        new FileChatMemoryStore(dir, 1, true);
        assertTrue(Files.exists(lockFile), "未过期孤儿锁文件不应被删除");
    }

    @Test
    void shouldHandleQuarantineMoveFailure() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-quarantine-move-fail-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir, 30, true);
        Path corruptedDir = dir.resolve("corrupted");
        Files.deleteIfExists(corruptedDir);
        Files.createFile(corruptedDir);

        String hashed = sha256("conv-quarantine-fail");
        Path file = dir.resolve(hashed + ".json");
        Files.writeString(file, "{invalid", StandardCharsets.UTF_8);

        assertThrows(IllegalStateException.class, () -> store.getMessages("conv-quarantine-fail"));
        assertTrue(Files.exists(file), "隔离失败时原文件应仍保留");
    }

    @Test
    void shouldCleanupExpiredJsonFilesOnStartup() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-retention-");
        FileChatMemoryStore firstStore = new FileChatMemoryStore(dir);
        firstStore.updateMessages("conv-expired", List.of(AiChatMessage.user("old-message")));
        firstStore.getMessages("conv-expired");

        String hashed = sha256("conv-expired");
        Path file = dir.resolve(hashed + ".json");
        Path lockFile = dir.resolve(hashed + ".lck");
        FileTime oldTime = FileTime.from(Instant.now().minus(Duration.ofDays(3)));
        Files.setLastModifiedTime(file, oldTime);
        Files.setLastModifiedTime(lockFile, oldTime);

        FileChatMemoryStore reloadedStore = new FileChatMemoryStore(dir, 1, true);
        assertTrue(Files.notExists(file), "过期会话文件应被删除");
        assertTrue(Files.notExists(lockFile), "过期会话锁文件应被删除");
        assertTrue(reloadedStore.getMessages("conv-expired").isEmpty(), "过期会话文件应在启动时被清理");
    }

    @Test
    void shouldCleanupOrphanLockFilesOnStartup() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-orphan-lock-");
        String hashed = sha256("conv-orphan");
        Path lockFile = dir.resolve(hashed + ".lck");
        Files.writeString(lockFile, "", StandardCharsets.UTF_8);
        FileTime oldTime = FileTime.from(Instant.now().minus(Duration.ofDays(2)));
        Files.setLastModifiedTime(lockFile, oldTime);

        new FileChatMemoryStore(dir, 1, true);
        assertTrue(Files.notExists(lockFile), "无关联 json 的过期锁文件应被删除");
    }

    @Test
    void shouldRejectNullMemoryId() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-null-id-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> store.getMessages(null));
        assertEquals("memoryId cannot be null", ex.getMessage());
    }

    @Test
    void shouldRejectNullMessageWhenAppending() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-null-append-message-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);

        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> store.append("conv-null-message", null));
        assertEquals("message", ex.getMessage());
    }

    @Test
    void shouldRejectNullCandidateWhenAppendIfAbsent() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-null-candidate-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);

        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> store.appendIfAbsent("conv-null-candidate", null, message -> false));
        assertEquals("candidate", ex.getMessage());
    }

    @Test
    void shouldRejectNullPredicateWhenAppendIfAbsent() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-null-predicate-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);

        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> store.appendIfAbsent("conv-null-predicate", AiChatMessage.user("x"), null));
        assertEquals("existsPredicate", ex.getMessage());
    }

    @Test
    void appendSystemMessageIfAbsentShouldHandleNonSystemAndDifferentSystemMessages() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-system-branches-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);
        store.updateMessages("conv-system-branches", List.of(
                AiChatMessage.user("u1"),
                AiChatMessage.system("old-system")
        ));

        boolean appended = store.appendSystemMessageIfAbsent("conv-system-branches", "new-system");
        assertTrue(appended);

        List<AiChatMessage> messages = store.getMessages("conv-system-branches");
        long newSystemCount = messages.stream()
                .filter(message -> message.role() == AiChatMessage.Role.SYSTEM)
                .filter(message -> "new-system".equals(message.content()))
                .count();
        assertEquals(1, newSystemCount);
    }

    @Test
    void withProcessLockShouldFailAfterRetryBudgetWhenOverlappingLockPersists() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-overlap-timeout-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);
        Path lockFile = dir.resolve("manual-key.lck");
        Files.writeString(lockFile, "", StandardCharsets.UTF_8);

        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            IOException root = invokeWithProcessLock(store, "manual-key", false);
            assertTrue(root.getMessage().contains("Failed to acquire process lock for key: manual-key"));
        }
    }

    @Test
    void withProcessLockShouldFailFastWhenThreadInterruptedDuringOverlapBackoff() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-overlap-interrupted-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);
        Path lockFile = dir.resolve("manual-key-int.lck");
        Files.writeString(lockFile, "", StandardCharsets.UTF_8);

        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            IOException root = invokeWithProcessLock(store, "manual-key-int", true);
            assertTrue(root.getMessage().contains("Interrupted while waiting process lock for key: manual-key-int"));
            assertTrue(Thread.currentThread().isInterrupted(), "中断标记应保留");
            Thread.interrupted();
        }
    }

    @Test
    void quarantineShouldNoOpWhenCorruptedFileAlreadyMissing() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-quarantine-missing-file-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir, 30, true);
        Method method = FileChatMemoryStore.class.getDeclaredMethod(
                "quarantineCorruptedFile", String.class, Path.class, RuntimeException.class);
        method.setAccessible(true);

        Path missing = dir.resolve("missing.json");
        method.invoke(store, "key-missing", missing, new IllegalStateException("bad-json"));
        assertTrue(Files.notExists(missing));
    }

    @Test
    void moveReplaceShouldFallbackWhenAtomicMoveNotSupported() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-atomic-fallback-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);
        Method method = FileChatMemoryStore.class.getDeclaredMethod("moveReplace", Path.class, Path.class);
        method.setAccessible(true);

        Path zip = Files.createTempFile("file-chat-memory-atomic-", ".zip");
        Files.deleteIfExists(zip);
        URI uri = URI.create("jar:" + zip.toUri());
        Path source = Files.createTempFile("file-chat-memory-src-", ".tmp");
        Files.writeString(source, "payload", StandardCharsets.UTF_8);

        try (FileSystem zipFs = FileSystems.newFileSystem(uri, Map.of("create", "true"))) {
            Path target = zipFs.getPath("/target.json");
            method.invoke(store, source, target);
            assertTrue(Files.notExists(source));
            assertEquals("payload", Files.readString(target));
        }
    }

    @Test
    void sha256ShouldThrowIllegalStateWhenAlgorithmUnavailable() throws Exception {
        Method method = FileChatMemoryStore.class.getDeclaredMethod("sha256", String.class, String.class);
        method.setAccessible(true);

        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> method.invoke(null, "x", "NOT-AN-ALGO"));
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("NOT-AN-ALGO not available"));
    }

    @Test
    void memoryKeyFromPathShouldReturnNullWhenFileNameIsMissing() {
        assertNull(FileChatMemoryStore.memoryKeyFromPath(Path.of("/"), ".json"));
    }

    @Test
    void memoryKeyFromPathShouldReturnNullWhenNameIsOnlySuffix() {
        assertNull(FileChatMemoryStore.memoryKeyFromPath(Path.of(".json"), ".json"));
        assertNull(FileChatMemoryStore.memoryKeyFromPath(Path.of(".lck"), ".lck"));
    }

    @Test
    void memoryKeyFromPathShouldExtractKeyForValidSuffix() {
        assertEquals("abc123", FileChatMemoryStore.memoryKeyFromPath(Path.of("abc123.json"), ".json"));
        assertEquals("lock-key", FileChatMemoryStore.memoryKeyFromPath(Path.of("lock-key.lck"), ".lck"));
    }

    private static IOException invokeWithProcessLock(FileChatMemoryStore store, String key, boolean interruptCurrentThread)
            throws Exception {
        Method method = FileChatMemoryStore.class.getDeclaredMethod("withProcessLock", String.class, Class.forName(
                "com.openmanus.infra.memory.FileChatMemoryStore$IoOperation"));
        method.setAccessible(true);
        Class<?> ioOpClass = Class.forName("com.openmanus.infra.memory.FileChatMemoryStore$IoOperation");
        Object ioOperation = Proxy.newProxyInstance(
                ioOpClass.getClassLoader(),
                new Class<?>[]{ioOpClass},
                (proxy, calledMethod, args) -> null
        );

        try {
            if (interruptCurrentThread) {
                Thread.currentThread().interrupt();
            }
            method.invoke(store, key, ioOperation);
            throw new AssertionError("Expected withProcessLock to fail under persistent overlap");
        } catch (InvocationTargetException e) {
            Throwable root = e.getCause();
            if (root instanceof IOException ioException) {
                return ioException;
            }
            throw e;
        }
    }

    private static String sha256(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
