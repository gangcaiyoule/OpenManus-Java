package com.openmanus.infra.memory;

import com.openmanus.aiframework.runtime.AiMemoryStore;
import com.openmanus.aiframework.runtime.model.AiChatMessage;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistentChatMemoryTest {

    @Test
    void shouldExposeIdAndSupportClear() {
        NonAtomicStore store = new NonAtomicStore();
        PersistentAiMemory memory = new PersistentAiMemory("conv-id", store);
        memory.add(AiChatMessage.user("u1"));

        assertEquals("conv-id", memory.id());
        assertEquals(1, memory.messages().size());

        memory.clear();
        assertEquals(0, memory.messages().size());
    }

    @Test
    void shouldUseAtomicAppendFastPathForFileStore() throws Exception {
        Path dir = Files.createTempDirectory("persistent-memory-file-store-fast-path-");
        FileChatMemoryStore fileStore = new FileChatMemoryStore(dir);
        PersistentAiMemory memory = new PersistentAiMemory("conv-file-fast", fileStore);

        memory.add(AiChatMessage.user("u1"));
        memory.add(AiChatMessage.user("u2"));

        assertEquals(2, memory.messages().size());
    }

    @Test
    void constructorShouldRejectNullArgs() {
        NonAtomicStore store = new NonAtomicStore();
        assertThrows(NullPointerException.class, () -> new PersistentAiMemory(null, store));
        assertThrows(NullPointerException.class, () -> new PersistentAiMemory("id", null));
    }

    @Test
    void shouldRejectNullMessageOnAdd() {
        NonAtomicStore store = new NonAtomicStore();
        PersistentAiMemory memory = new PersistentAiMemory("conv-null-message", store);

        assertThrows(NullPointerException.class, () -> memory.add(null));
    }

    @Test
    void shouldDelegateEnsureSystemMessageToAtomicStoreCapability() {
        AtomicStore store = new AtomicStore();
        PersistentAiMemory memory = new PersistentAiMemory("conv-a", store);

        boolean inserted = memory.ensureSystemMessage("sys");
        assertTrue(inserted);
        assertEquals(1, store.atomicCalls.get(), "应走原子 appendIfAbsent 能力");
    }

    @Test
    void shouldDelegateAddToAtomicStoreCapability() {
        AtomicStore store = new AtomicStore();
        PersistentAiMemory memory = new PersistentAiMemory("conv-a-add", store);

        memory.add(AiChatMessage.user("u1"));

        assertEquals(1, store.appendCalls.get(), "add 应走原子 append 能力");
    }

    @Test
    void shouldReturnFalseWhenSystemMessageAlreadyExistsForAtomicStore() {
        AtomicStore store = new AtomicStore();
        PersistentAiMemory memory = new PersistentAiMemory("conv-a", store);
        assertTrue(memory.ensureSystemMessage("sys"));
        assertFalse(memory.ensureSystemMessage("sys"));
    }

    @Test
    void shouldHandleNullEntriesWhenEnsuringSystemMessageForAtomicStore() {
        AtomicStore store = new AtomicStore();
        store.updateMessages("conv-null-atomic", Arrays.asList((AiChatMessage) null));
        PersistentAiMemory memory = new PersistentAiMemory("conv-null-atomic", store);

        assertTrue(memory.ensureSystemMessage("sys"));
        assertEquals(2, store.getMessages("conv-null-atomic").size());
    }

    @Test
    void shouldIgnoreNonSystemEntriesWhenEnsuringSystemMessageForAtomicStore() {
        AtomicStore store = new AtomicStore();
        store.updateMessages("conv-non-system-atomic", List.of(AiChatMessage.user("u")));
        PersistentAiMemory memory = new PersistentAiMemory("conv-non-system-atomic", store);

        assertTrue(memory.ensureSystemMessage("sys"));
        assertEquals(2, store.getMessages("conv-non-system-atomic").size());
    }

    @Test
    void shouldIgnoreDifferentSystemTextWhenEnsuringSystemMessageForAtomicStore() {
        AtomicStore store = new AtomicStore();
        store.updateMessages("conv-diff-system-atomic", List.of(AiChatMessage.system("old")));
        PersistentAiMemory memory = new PersistentAiMemory("conv-diff-system-atomic", store);

        assertTrue(memory.ensureSystemMessage("new"));
        assertEquals(2, store.getMessages("conv-diff-system-atomic").size());
    }

    @Test
    void shouldAvoidDuplicateSystemMessageOnConcurrentEnsureForFallbackStore() throws Exception {
        NonAtomicStore store = new NonAtomicStore();
        PersistentAiMemory memoryA = new PersistentAiMemory("conv-b", store);
        PersistentAiMemory memoryB = new PersistentAiMemory("conv-b", store);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        for (PersistentAiMemory memory : List.of(memoryA, memoryB)) {
            executor.submit(() -> {
                try {
                    start.await(30, TimeUnit.SECONDS);
                    memory.ensureSystemMessage("sys");
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
                .filter(message -> message.role() == AiChatMessage.Role.SYSTEM)
                .count();
        assertEquals(1, systemCount, "并发 ensureSystemMessage 不应写入重复 system message");
    }

    @Test
    void shouldReturnFalseWhenSystemMessageAlreadyExistsForFallbackStore() {
        NonAtomicStore store = new NonAtomicStore();
        PersistentAiMemory memory = new PersistentAiMemory("conv-fallback", store);
        assertTrue(memory.ensureSystemMessage("sys"));
        assertFalse(memory.ensureSystemMessage("sys"));
        assertEquals(1, memory.messages().size());
    }

    @Test
    void shouldHandleNullEntriesWhenEnsuringSystemMessageForFallbackStore() {
        NonAtomicStore store = new NonAtomicStore();
        store.updateMessages("conv-null-fallback", Arrays.asList((AiChatMessage) null));
        PersistentAiMemory memory = new PersistentAiMemory("conv-null-fallback", store);

        assertTrue(memory.ensureSystemMessage("sys"));
        assertEquals(2, memory.messages().size());
    }

    @Test
    void shouldIgnoreNonSystemEntriesWhenEnsuringSystemMessageForFallbackStore() {
        NonAtomicStore store = new NonAtomicStore();
        store.updateMessages("conv-non-system-fallback", List.of(AiChatMessage.user("u")));
        PersistentAiMemory memory = new PersistentAiMemory("conv-non-system-fallback", store);

        assertTrue(memory.ensureSystemMessage("sys"));
        assertEquals(2, memory.messages().size());
    }

    @Test
    void shouldIgnoreDifferentSystemTextWhenEnsuringSystemMessageForFallbackStore() {
        NonAtomicStore store = new NonAtomicStore();
        store.updateMessages("conv-diff-system-fallback", List.of(AiChatMessage.system("old")));
        PersistentAiMemory memory = new PersistentAiMemory("conv-diff-system-fallback", store);

        assertTrue(memory.ensureSystemMessage("new"));
        assertEquals(2, memory.messages().size());
    }

    @Test
    void shouldNotLoseMessagesOnConcurrentAddForFallbackStore() throws Exception {
        NonAtomicStore store = new NonAtomicStore();
        PersistentAiMemory memoryA = new PersistentAiMemory("conv-c", store);
        PersistentAiMemory memoryB = new PersistentAiMemory("conv-c", store);

        int perWorker = 100;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        executor.submit(() -> {
            try {
                start.await(30, TimeUnit.SECONDS);
                for (int i = 0; i < perWorker; i++) {
                    memoryA.add(AiChatMessage.user("a-" + i));
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
                    memoryB.add(AiChatMessage.user("b-" + i));
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
        private final AtomicInteger appendCalls = new AtomicInteger(0);

        @Override
        public void append(Object memoryId, AiChatMessage message) {
            appendCalls.incrementAndGet();
            List<AiChatMessage> current = new ArrayList<>(getMessages(memoryId));
            current.add(message);
            updateMessages(memoryId, current);
        }

        @Override
        public boolean appendIfAbsent(Object memoryId,
                                      AiChatMessage candidate,
                                      Predicate<AiChatMessage> existsPredicate) {
            atomicCalls.incrementAndGet();
            List<AiChatMessage> current = new ArrayList<>(getMessages(memoryId));
            if (current.stream().anyMatch(existsPredicate)) {
                return false;
            }
            current.add(candidate);
            updateMessages(memoryId, current);
            return true;
        }
    }

    private static class NonAtomicStore implements AiMemoryStore {
        private final ConcurrentHashMap<String, List<AiChatMessage>> data = new ConcurrentHashMap<>();

        @Override
        public List<AiChatMessage> getMessages(Object memoryId) {
            return new ArrayList<>(data.getOrDefault(String.valueOf(memoryId), List.of()));
        }

        @Override
        public void updateMessages(Object memoryId, List<AiChatMessage> messages) {
            data.put(String.valueOf(memoryId), new ArrayList<>(messages));
        }

        @Override
        public void deleteMessages(Object memoryId) {
            data.remove(String.valueOf(memoryId));
        }
    }
}
