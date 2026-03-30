package com.openmanus.aiframework.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.aiframework.model.ChatResponseEnvelope;
import com.openmanus.aiframework.model.ProviderStreamChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiResponseParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldParseSyncTextAndFunctionCall() throws Exception {
        GeminiResponseParser parser = new GeminiResponseParser();
        ChatResponseEnvelope parsed = parser.parse(objectMapper.readTree("""
                {
                  "candidates": [{
                    "content": {
                      "parts": [
                        {"text": "hello"},
                        {"functionCall": {"name": "search", "args": {"q": "today"}}}
                      ]
                    },
                    "finishReason": "STOP"
                  }],
                  "usageMetadata": {"promptTokenCount": 7}
                }
                """));

        assertEquals("hello", parsed.getContent());
        assertEquals("STOP", parsed.getFinishReason());
        assertEquals(1, parsed.getToolCalls().size());
        assertEquals("search", parsed.getToolCalls().get(0).path("name").asText());
    }

    @Test
    void shouldParseStreamDeltaAndCompletion() throws Exception {
        GeminiResponseParser parser = new GeminiResponseParser();

        ProviderStreamChunk delta = parser.parseStreamChunk("message", objectMapper.readTree("""
                {
                  "candidates": [{
                    "content": {
                      "parts": [
                        {"text": "abc"},
                        {"functionCall": {"name": "search", "args": {"q": "today"}}}
                      ]
                    }
                  }]
                }
                """));
        assertEquals("abc", delta.getDeltaText());
        assertEquals(1, delta.getToolCalls().size());
        assertFalse(delta.isCompleted());

        ProviderStreamChunk stop = parser.parseStreamChunk("message", objectMapper.readTree("""
                {
                  "candidates": [{"finishReason": "STOP"}],
                  "usageMetadata": {"totalTokenCount": 10}
                }
                """));
        assertTrue(stop.isCompleted());
        assertEquals("STOP", stop.getFinishReason());
        assertEquals(10, stop.getUsage().path("totalTokenCount").asInt());
    }
}
