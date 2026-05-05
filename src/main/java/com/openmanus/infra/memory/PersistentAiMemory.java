package com.openmanus.infra.memory;

import com.openmanus.aiframework.runtime.AiMemory;
import com.openmanus.aiframework.runtime.AiSystemMessageMemory;
import com.openmanus.aiframework.runtime.AiMemoryStore;
import com.openmanus.aiframework.runtime.model.AiChatMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Unbounded runtime chat memory backed by AiMemoryStore.
 * Keeps full message history for one conversation id.
 */
public class PersistentAiMemory implements AiMemory, AiSystemMessageMemory {

  private static final int FALLBACK_LOCK_STRIPES = 1024;
  private static final ReentrantLock[] FALLBACK_LOCKS = createFallbackLocks();

  private final Object id;
  private final AiMemoryStore store;

  public PersistentAiMemory(Object id, AiMemoryStore store) {
    this.id = Objects.requireNonNull(id, "id");
    this.store = Objects.requireNonNull(store, "store");
  }

  @Override
  public Object id() {
    return id;
  }

  @Override
  public void add(AiChatMessage message) {
    Objects.requireNonNull(message, "message");
    if (store instanceof AtomicAppendChatMemoryStore atomicStore) {
      atomicStore.append(id, message);
      return;
    }
    ReentrantLock lock = lockForFallback(id);
    lock.lock();
    try {
      List<AiChatMessage> current = new ArrayList<>(store.getMessages(id));
      current.add(message);
      store.updateMessages(id, current);
    } finally {
      lock.unlock();
    }
  }

  /**
   * Ensure one matching system message exists exactly once in this memory.
   */
  @Override
  public boolean ensureSystemMessage(String systemText) {
    AiChatMessage systemMessage = AiChatMessage.system(systemText);
    if (store instanceof AtomicAppendChatMemoryStore atomicStore) {
      return atomicStore.appendIfAbsent(
          id,
          systemMessage,
          message -> message != null
              && message.role() == AiChatMessage.Role.SYSTEM
              && Objects.equals(message.content(), systemText));
    }
    ReentrantLock lock = lockForFallback(id);
    lock.lock();
    try {
      List<AiChatMessage> current = new ArrayList<>(store.getMessages(id));
      boolean exists = current.stream()
          .anyMatch(message -> message != null
              && message.role() == AiChatMessage.Role.SYSTEM
              && Objects.equals(message.content(), systemText));
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
  public List<AiChatMessage> messages() {
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
