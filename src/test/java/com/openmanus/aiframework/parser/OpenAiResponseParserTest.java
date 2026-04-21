package com.openmanus.aiframework.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.aiframework.exception.AiFrameworkException;
import com.openmanus.aiframework.model.ProviderStreamChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiResponseParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldParseResponsesApiDeltaEvent() throws Exception {
        OpenAiResponseParser parser = new OpenAiResponseParser();
        ProviderStreamChunk chunk = parser.parseStreamChunk("message",
                objectMapper.readTree("""
                        {
                          "type": "response.output_text.delta",
                          "delta": "hello"
                        }
                        """));

        assertEquals("hello", chunk.getDeltaText());
        assertFalse(chunk.isCompleted());
    }

    @Test
    void shouldParseResponsesApiCompletedEvent() throws Exception {
        OpenAiResponseParser parser = new OpenAiResponseParser();
        ProviderStreamChunk chunk = parser.parseStreamChunk("message",
                objectMapper.readTree("""
                        {
                          "type": "response.completed",
                          "response": {
                            "status": "completed",
                            "usage": {"total_tokens": 42}
                          }
                        }
                        """));

        assertTrue(chunk.isCompleted());
        assertEquals("completed", chunk.getFinishReason());
        assertEquals(42, chunk.getUsage().path("total_tokens").asInt());
    }

    @Test
    void shouldFailWhenStreamChunkContainsProviderErrorPayload() throws Exception {
        OpenAiResponseParser parser = new OpenAiResponseParser();

        AiFrameworkException error = assertThrows(AiFrameworkException.class,
                () -> parser.parseStreamChunk("message", objectMapper.readTree("""
                        {
                          "error": {
                            "message": "No available channel",
                            "type": "new_api_error",
                            "code": "model_not_found"
                          }
                        }
                        """)));

        assertEquals("Provider returned error payload: type=new_api_error, code=model_not_found, message=No available channel",
                error.getMessage());
    }
}
