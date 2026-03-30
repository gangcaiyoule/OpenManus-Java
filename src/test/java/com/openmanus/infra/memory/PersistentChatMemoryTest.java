package com.openmanus.infra.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistentChatMemoryTest {

    @Test
    void shouldDelegateEnsureSystemMessageToAtomicStoreCapability() {
        AtomicStore store = new AtomicStore();
        PersistentChatMemory memory = new PersistentChatMemory("conv-a", store);

        boolean inserted = memory.ensureSystemMessage(SystemMessage.from("sys"));
        assertTrue(inserted);
        assertEquals(1, store.atomicCalls.get(), "应走原子 appendIfAbsent 能力");
    }

    @Test
    void shouldAvoidDuplicateSystemMessageOnConcurrentEnsureForFallbackStore() throws Exception {
        NonAtomicStore store = new NonAtomicStore();
        PersistentChatMemory memoryA = new PersistentChatMemory("conv-b", store);
        PersistentChatMemory memoryB = new PersistentChatMemory("conv-b", store);
        SystemMessage systemMessage = SystemMessage.from("sys");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        for (PersistentChatMemory memory : List.of(memoryA, memoryB)) {
            executor.submit(() -> {
                try {
                    start.await(30, TimeUnit.SECONDS);
                    memory.ensureSystemMessage(systemMessage);
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

        long systemCount = store.getMessages("conv-b").stream()
                .filter(message -> message instanceof SystemMessage)
                .count();
        assertEquals(1, systemCount, "并发 ensureSystemMessage 不应写入重复 system message");
    }

    @Test
    void shouldNotLoseMessagesOnConcurrentAddForFallbackStore() throws Exception {
        NonAtomicStore store = new NonAtomicStore();
        PersistentChatMemory memoryA = new PersistentChatMemory("conv-c", store);
        PersistentChatMemory memoryB = new PersistentChatMemory("conv-c", store);

        int perWorker = 100;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        executor.submit(() -> {
            try {
                start.await(30, TimeUnit.SECONDS);
                for (int i = 0; i < perWorker; i++) {
                    memoryA.add(UserMessage.from("a-" + i));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });
        executor.submit(() -> {
            try {
                start.await(30, TimeUnit.SECONDS);
                for (int i = 0; i < perWorker; i++) {
                    memoryB.add(UserMessage.from("b-" + i));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        executor.shutdownNow();

        assertEquals(2L * perWorker, store.getMessages("conv-c").size(), "并发 add 不应丢消息");
    }

    private static class AtomicStore extends NonAtomicStore implements AtomicAppendChatMemoryStore {
        private final AtomicInteger atomicCalls = new AtomicInteger(0);

        @Override
        public boolean appendIfAbsent(Object memoryId, ChatMessage candidate, Predicate<ChatMessage> existsPredicate) {
            atomicCalls.incrementAndGet();
            List<ChatMessage> current = new ArrayList<>(getMessages(memoryId));
            if (current.stream().anyMatch(existsPredicate)) {
                return false;
            }
            current.add(candidate);
            updateMessages(memoryId, current);
            return true;
        }
    }

    private static class NonAtomicStore implements ChatMemoryStore {
        private final ConcurrentHashMap<String, List<ChatMessage>> data = new ConcurrentHashMap<>();

        @Override
        public List<ChatMessage> getMessages(Object memoryId) {
            return new ArrayList<>(data.getOrDefault(String.valueOf(memoryId), List.of()));
        }

        @Override
        public void updateMessages(Object memoryId, List<ChatMessage> messages) {
            data.put(String.valueOf(memoryId), new ArrayList<>(messages));
        }

        @Override
        public void deleteMessages(Object memoryId) {
            data.remove(String.valueOf(memoryId));
        }
    }
}
