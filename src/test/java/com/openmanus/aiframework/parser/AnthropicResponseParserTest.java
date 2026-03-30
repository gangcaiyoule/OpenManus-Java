package com.openmanus.aiframework.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.aiframework.model.ChatResponseEnvelope;
import com.openmanus.aiframework.model.ProviderStreamChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnthropicResponseParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldParseSyncTextAndToolUse() throws Exception {
        var root = objectMapper.readTree("""
                {
                  "content": [
                    {"type":"text","text":"hello "},
                    {"type":"tool_use","id":"toolu_1","name":"search","input":{"q":"weather"}},
                    {"type":"text","text":"world"}
                  ],
                  "stop_reason": "end_turn",
                  "usage": {"input_tokens": 10, "output_tokens": 20}
                }
                """);

        AnthropicResponseParser parser = new AnthropicResponseParser();
        ChatResponseEnvelope parsed = parser.parse(root);

        assertEquals("hello world", parsed.getContent());
        assertEquals(1, parsed.getToolCalls().size());
        assertEquals("end_turn", parsed.getFinishReason());
        assertEquals(10, parsed.getUsage().path("input_tokens").asInt());
    }

    @Test
    void shouldParseStreamDeltaAndCompletion() throws Exception {
        AnthropicResponseParser parser = new AnthropicResponseParser();

        ProviderStreamChunk delta = parser.parseStreamChunk("content_block_delta",
                objectMapper.readTree("{" +
                        "\"delta\":{\"type\":\"text_delta\",\"text\":\"abc\"}" +
                        "}"));
        assertEquals("abc", delta.getDeltaText());

        ProviderStreamChunk stop = parser.parseStreamChunk("message_stop", objectMapper.readTree("{}"));
        assertTrue(stop.isCompleted());
    }
}
