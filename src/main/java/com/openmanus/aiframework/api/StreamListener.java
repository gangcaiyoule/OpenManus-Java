package com.openmanus.aiframework.api;

import com.openmanus.aiframework.model.ChatResponseEnvelope;

public interface StreamListener {

    void onDelta(String deltaText);

    void onToolCall(String providerRawToolCallJson);

    void onComplete(ChatResponseEnvelope finalResponse);

    void onError(Throwable error);
}
