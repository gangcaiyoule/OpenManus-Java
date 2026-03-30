package com.openmanus.aiframework.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.openmanus.aiframework.model.ChatResponseEnvelope;
import com.openmanus.aiframework.model.ProviderStreamChunk;

public interface ProviderResponseParser {

    ChatResponseEnvelope parse(JsonNode root);

    ProviderStreamChunk parseStreamChunk(String eventType, JsonNode chunk);
}
