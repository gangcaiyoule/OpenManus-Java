package com.openmanus.infra.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Unbounded chat memory backed by ChatMemoryStore.
 * Keeps full message history for one conversation id.
 */
public class PersistentChatMemory implements ChatMemory {

    private static final int FALLBACK_LOCK_STRIPES = 1024;
    private static final ReentrantLock[] FALLBACK_LOCKS = createFallbackLocks();

    private final Object id;
    private final ChatMemoryStore store;

    public PersistentChatMemory(Object id, ChatMemoryStore store) {
        this.id = Objects.requireNonNull(id, "id");
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public Object id() {
        return id;
    }

    @Override
    public void add(ChatMessage chatMessage) {
        if (store instanceof FileChatMemoryStore fileStore) {
            fileStore.appendMessage(id, chatMessage);
            return;
        }
        ReentrantLock lock = lockForFallback(id);
        lock.lock();
        try {
            List<ChatMessage> current = new ArrayList<>(store.getMessages(id));
            current.add(chatMessage);
            store.updateMessages(id, current);
        } finally {
            lock.unlock();
        }
    }

    public boolean ensureSystemMessage(SystemMessage systemMessage) {
        if (store instanceof AtomicAppendChatMemoryStore atomicStore) {
            return atomicStore.appendIfAbsent(
                    id,
                    systemMessage,
                    message -> message instanceof SystemMessage existing
                            && existing.text().equals(systemMessage.text()));
        }
        ReentrantLock lock = lockForFallback(id);
        lock.lock();
        try {
            List<ChatMessage> current = new ArrayList<>(store.getMessages(id));
            boolean exists = current.stream()
                    .anyMatch(message -> message instanceof SystemMessage existing
                            && existing.text().equals(systemMessage.text()));
            if (exists) {
                return false;
            }
            current.add(systemMessage);
            store.updateMessages(id, current);
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<ChatMessage> messages() {
        return new ArrayList<>(store.getMessages(id));
    }

    @Override
    public void clear() {
        store.deleteMessages(id);
    }

    private static ReentrantLock lockForFallback(Object memoryId) {
        String key = String.valueOf(memoryId);
        int index = Math.floorMod(key.hashCode(), FALLBACK_LOCK_STRIPES);
        return FALLBACK_LOCKS[index];
    }

    private static ReentrantLock[] createFallbackLocks() {
        ReentrantLock[] locks = new ReentrantLock[FALLBACK_LOCK_STRIPES];
        for (int i = 0; i < FALLBACK_LOCK_STRIPES; i++) {
            locks[i] = new ReentrantLock();
        }
        return locks;
    }
}
