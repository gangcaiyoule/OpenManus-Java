package com.openmanus.infra.memory;

import com.openmanus.aiframework.runtime.model.AiChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryAiMemoryStoreTest {

    @Test
    void shouldRejectNullMemoryIdAcrossOperations() {
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();

        assertThrows(IllegalArgumentException.class, () -> store.getMessages(null));
        assertThrows(IllegalArgumentException.class, () -> store.updateMessages(null, List.of(AiChatMessage.user("x"))));
        assertThrows(IllegalArgumentException.class, () -> store.deleteMessages(null));
        assertThrows(IllegalArgumentException.class, () -> store.appendIfAbsent(
                null,
                AiChatMessage.system("sys"),
                message -> false
        ));
    }

    @Test
    void shouldRejectNullAppendIfAbsentArguments() {
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();

        assertThrows(NullPointerException.class, () -> store.appendIfAbsent(
                "conv",
                null,
                message -> false
        ));
        assertThrows(NullPointerException.class, () -> store.appendIfAbsent(
                "conv",
                AiChatMessage.system("sys"),
                null
        ));
    }

    @Test
    void shouldAvoidDuplicateSystemMessageOnConcurrentAppendIfAbsent() throws Exception {
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        String memoryId = "conv-concurrent";
        AiChatMessage system = AiChatMessage.system("sys");

        int workers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);

        for (int i = 0; i < workers; i++) {
            executor.submit(() -> {
                try {
                    start.await(30, TimeUnit.SECONDS);
                    store.appendIfAbsent(memoryId, system,
                            message -> message != null
                                    && message.role() == AiChatMessage.Role.SYSTEM
                                    && "sys".equals(message.content()));
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

        long systemCount = store.getMessages(memoryId).stream()
                .filter(message -> message != null
                        && message.role() == AiChatMessage.Role.SYSTEM
                        && "sys".equals(message.content()))
                .count();
        assertEquals(1, systemCount);
    }

    @Test
    void shouldIgnoreNullEntriesWhenCheckingExistsPredicate() {
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        String memoryId = "conv-null-entry";
        store.updateMessages(memoryId, java.util.Arrays.asList((AiChatMessage) null));

        boolean appended = store.appendIfAbsent(
                memoryId,
                AiChatMessage.system("sys"),
                message -> message.role() == AiChatMessage.Role.SYSTEM
                        && "sys".equals(message.content())
        );

        assertTrue(appended);
        assertEquals(2, store.getMessages(memoryId).size());
    }
}
