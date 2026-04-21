package com.openmanus.infra.memory;

import dev.langchain4j.data.message.ChatMessage;

import java.util.function.Predicate;

/**
 * Store capability for atomic append-if-absent.
 */
public interface AtomicAppendChatMemoryStore {

    boolean appendIfAbsent(Object memoryId, ChatMessage candidate, Predicate<ChatMessage> existsPredicate);
}
