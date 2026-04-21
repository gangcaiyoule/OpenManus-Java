package com.openmanus.aiframework.runtime;

import com.openmanus.aiframework.runtime.model.AiChatMessage;

import java.util.List;

public interface AiMemoryStore {

    List<AiChatMessage> getMessages(Object memoryId);

    void updateMessages(Object memoryId, List<AiChatMessage> messages);

    void deleteMessages(Object memoryId);
}
