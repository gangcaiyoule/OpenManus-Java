package com.openmanus.aiframework.api;

import com.openmanus.aiframework.model.ChatRequestEnvelope;
import com.openmanus.aiframework.model.ChatResponseEnvelope;

public interface AiProviderClient {

    ChatResponseEnvelope chat(ChatRequestEnvelope request);

    void streamChat(ChatRequestEnvelope request, StreamListener listener);
}
