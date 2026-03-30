package com.openmanus.infra.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileChatMemoryStoreTest {

    @Test
    void shouldPersistMessagesToDisk() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-");
        FileChatMemoryStore firstStore = new FileChatMemoryStore(dir);

        firstStore.updateMessages("conv-1", List.of(UserMessage.from("hello")));

        FileChatMemoryStore secondStore = new FileChatMemoryStore(dir);
        List<ChatMessage> restored = secondStore.getMessages("conv-1");

        assertEquals(1, restored.size());
        assertTrue(restored.getFirst().toString().contains("hello"));
    }

    @Test
    void shouldDeleteMessages() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-delete-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);
        store.updateMessages("conv-2", List.of(UserMessage.from("bye")));
        store.getMessages("conv-2"); // force-create lock file

        String hashed = sha256("conv-2");
        Path lockFile = dir.resolve(hashed + ".lck");
        assertTrue(Files.exists(lockFile), "访问后应生成锁文件");

        store.deleteMessages("conv-2");

        assertTrue(Files.notExists(lockFile), "删除会话时应同步清理锁文件");
        assertTrue(store.getMessages("conv-2").isEmpty());
    }

    @Test
    void shouldAppendConcurrentlyWithoutLosingMessages() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-concurrency-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);
        PersistentChatMemory memory = new PersistentChatMemory("conv-c", store);

        int tasks = 40;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(tasks);

        for (int i = 0; i < tasks; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    start.await(30, TimeUnit.SECONDS);
                    memory.add(UserMessage.from("msg-" + idx));
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

        List<ChatMessage> messages = new ArrayList<>(memory.messages());
        assertEquals(tasks, messages.size(), "并发 append 不应丢消息");
    }

    @Test
    void shouldAppendConcurrentlyAcrossStoreInstancesWithoutLosingMessages() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-multi-store-");
        FileChatMemoryStore storeA = new FileChatMemoryStore(dir);
        FileChatMemoryStore storeB = new FileChatMemoryStore(dir);
        PersistentChatMemory memoryA = new PersistentChatMemory("conv-shared", storeA);
        PersistentChatMemory memoryB = new PersistentChatMemory("conv-shared", storeB);

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
                        memoryA.add(UserMessage.from("a-" + idx));
                    } else {
                        memoryB.add(UserMessage.from("b-" + idx));
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

        List<ChatMessage> messages = new ArrayList<>(memoryA.messages());
        assertEquals(tasks, messages.size(), "跨 store 实例并发 append 不应丢消息");
    }

    @Test
    void shouldFailFastWhenStoredJsonIsCorrupted() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-corrupt-");
        FileChatMemoryStore store = new FileChatMemoryStore(dir);
        store.updateMessages("conv-bad", List.of(UserMessage.from("ok")));

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
    void shouldCleanupExpiredJsonFilesOnStartup() throws Exception {
        Path dir = Files.createTempDirectory("file-chat-memory-retention-");
        FileChatMemoryStore firstStore = new FileChatMemoryStore(dir);
        firstStore.updateMessages("conv-expired", List.of(UserMessage.from("old-message")));
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

    private static String sha256(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
