package com.openmanus.aiframework.runtime;

import com.openmanus.aiframework.runtime.model.AiChatMessage;

import java.util.List;

public interface AiMemory {

    Object id();

    List<AiChatMessage> messages();

    void add(AiChatMessage message);

    default void addAll(List<AiChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        messages.forEach(this::add);
    }

    void clear();
}
