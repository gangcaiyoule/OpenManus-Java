package com.openmanus.infra.memory;

import com.openmanus.aiframework.runtime.AiMemoryStore;
import com.openmanus.aiframework.runtime.model.AiChatMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * In-memory AiMemoryStore implementation for local runtime/testing usage.
 */
public class InMemoryAiMemoryStore implements AiMemoryStore, AtomicAppendChatMemoryStore {
  private final ConcurrentHashMap<String, List<AiChatMessage>> data = new ConcurrentHashMap<>();

  @Override
  public List<AiChatMessage> getMessages(Object memoryId) {
    return new ArrayList<>(data.getOrDefault(memoryKey(memoryId), List.of()));
  }

  @Override
  public void updateMessages(Object memoryId, List<AiChatMessage> messages) {
    data.put(memoryKey(memoryId), new ArrayList<>(messages == null ? List.of() : messages));
  }

  @Override
  public void deleteMessages(Object memoryId) {
    data.remove(memoryKey(memoryId));
  }

  @Override
  public void append(Object memoryId, AiChatMessage message) {
    Objects.requireNonNull(message, "message");
    String key = memoryKey(memoryId);
    data.compute(
        key,
        (ignored, existing) -> {
          List<AiChatMessage> current = new ArrayList<>(existing == null ? List.of() : existing);
          current.add(message);
          return current;
        });
  }

  @Override
  public boolean appendIfAbsent(
      Object memoryId, AiChatMessage candidate, Predicate<AiChatMessage> existsPredicate) {
    Objects.requireNonNull(candidate, "candidate");
    Objects.requireNonNull(existsPredicate, "existsPredicate");
    String key = memoryKey(memoryId);
    AtomicBoolean appended = new AtomicBoolean(false);
    data.compute(
        key,
        (ignored, existing) -> {
          List<AiChatMessage> current = new ArrayList<>(existing == null ? List.of() : existing);
          if (current.stream().filter(Objects::nonNull).anyMatch(existsPredicate)) {
            return current;
          }
          current.add(candidate);
          appended.set(true);
          return current;
        });
    return appended.get();
  }

  private static String memoryKey(Object memoryId) {
    if (memoryId == null) {
      throw new IllegalArgumentException("memoryId cannot be null");
    }
    return String.valueOf(memoryId);
  }
}
