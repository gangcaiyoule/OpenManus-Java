package com.openmanus.agent.context;

import com.openmanus.aiframework.runtime.model.AiChatMessage;
import com.openmanus.aiframework.runtime.model.AiToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelContextTokenCounterBaselineTest {

    @Test
    void shouldKeepStableBaselineForAsciiInputAcrossCounterModes() {
        AiChatMessage message = AiChatMessage.user("open manus tokenizer baseline");

        assertEquals(16, ApproxModelContextTokenCounter.getInstance().estimateTokens(message));
        assertEquals(12, TokenizerModelContextTokenCounter.getInstance().estimateTokens(message));
        assertEquals(9, ModelTokenizerModelContextTokenCounter.getInstance().estimateTokens(message));
    }

    @Test
    void shouldKeepStableBaselineForMixedCjkInputAcrossCounterModes() {
        AiChatMessage message = AiChatMessage.user("你好世界 token baseline");

        assertEquals(13, ApproxModelContextTokenCounter.getInstance().estimateTokens(message));
        assertEquals(12, TokenizerModelContextTokenCounter.getInstance().estimateTokens(message));
        assertEquals(12, ModelTokenizerModelContextTokenCounter.getInstance().estimateTokens(message));
    }

    @Test
    void shouldKeepStableBaselineForAssistantToolCallInputAcrossCounterModes() {
        AiChatMessage message = AiChatMessage.assistant(
                " ",
                List.of(new AiToolCall("call_1", "searchWeb", "{\"query\":\"open manus\"}"))
        );

        assertEquals(27, ApproxModelContextTokenCounter.getInstance().estimateTokens(message));
        assertEquals(23, TokenizerModelContextTokenCounter.getInstance().estimateTokens(message));
        assertEquals(19, ModelTokenizerModelContextTokenCounter.getInstance().estimateTokens(message));
    }
}
