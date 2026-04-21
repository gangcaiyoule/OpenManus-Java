package com.openmanus.agent.context;

import com.openmanus.aiframework.runtime.model.AiChatMessage;
import com.openmanus.aiframework.runtime.model.AiToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenizerModelContextTokenCounterTest {

    private final TokenizerModelContextTokenCounter counter = TokenizerModelContextTokenCounter.getInstance();

    @Test
    void shouldCountAsciiWordsByTokenBoundaries() {
        int tokens = counter.estimateTokens(AiChatMessage.user("open manus tokenizer test"));
        assertEquals(11, tokens);
    }

    @Test
    void shouldCountCjkCharactersIndividually() {
        int cjkTokens = TokenizerModelContextTokenCounter.estimateTextTokens("你好世界");
        int asciiTokens = TokenizerModelContextTokenCounter.estimateTextTokens("hello world");

        assertEquals(4, cjkTokens);
        assertEquals(4, asciiTokens);
    }

    @Test
    void shouldIncludeToolCallPayloadWhenAssistantContentIsBlank() {
        AiChatMessage assistant = AiChatMessage.assistant(
                " ",
                List.of(new AiToolCall("call_1", "searchWeb", "{\"query\":\"open manus\"}"))
        );

        int tokens = counter.estimateTokens(assistant);
        assertTrue(tokens > 8);
    }
}
