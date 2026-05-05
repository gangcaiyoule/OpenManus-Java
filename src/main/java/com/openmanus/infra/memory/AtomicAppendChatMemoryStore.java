package com.openmanus.infra.memory;

import com.openmanus.aiframework.runtime.model.AiChatMessage;
import java.util.function.Predicate;

/**
 * Store capability for atomic append operations.
 */
public interface AtomicAppendChatMemoryStore {

  void append(Object memoryId, AiChatMessage message);

  boolean appendIfAbsent(
      Object memoryId,
      AiChatMessage candidate,
      Predicate<AiChatMessage> existsPredicate);
}
