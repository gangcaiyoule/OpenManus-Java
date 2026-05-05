package com.openmanus.aiframework.runtime;

import com.openmanus.aiframework.runtime.model.AiChatRequest;
import com.openmanus.aiframework.runtime.model.AiChatResponse;

public interface AiChatModel {

    AiChatResponse chat(AiChatRequest request);
}
