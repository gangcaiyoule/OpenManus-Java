package com.openmanus.agent.context;

import com.knuddels.jtokkit.api.EncodingType;
import com.openmanus.aiframework.runtime.model.AiChatMessage;
import com.openmanus.aiframework.runtime.model.AiToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelTokenizerModelContextTokenCounterTest {

    private final ModelTokenizerModelContextTokenCounter counter =
            ModelTokenizerModelContextTokenCounter.getInstance();

    @Test
    void shouldCountMixedLanguageAndSymbolPayload() {
        AiChatMessage user = AiChatMessage.user("open manus 你好, count tokens please!");

        int tokens = counter.estimateTokens(user);

        assertTrue(tokens > 8);
    }

    @Test
    void shouldIncludeAssistantToolCallsIntoTokenCount() {
        AiChatMessage assistant = AiChatMessage.assistant(
                " ",
                List.of(new AiToolCall("call_1", "searchWeb", "{\"query\":\"open manus tokenizer\"}"))
        );

        int tokens = counter.estimateTokens(assistant);

        assertTrue(tokens > 10);
    }

    @Test
    void shouldCountLongAlphanumericContentWithoutSevereUndercount() {
        AiChatMessage user = AiChatMessage.user("A".repeat(120));

        int modelTokens = counter.estimateTokens(user);
        int approxTokens = ApproxModelContextTokenCounter.getInstance().estimateTokens(user);

        assertTrue(modelTokens >= approxTokens / 2);
    }

    @Test
    void shouldResolveO200kEncodingForModernModelFamilies() {
        ModelTokenizerModelContextTokenCounter counter =
                ModelTokenizerModelContextTokenCounter.forModel("gpt-4o-mini");

        assertEquals(EncodingType.O200K_BASE, counter.encodingType());
    }

    @Test
    void shouldFallbackToCl100kEncodingWhenModelIsUnknown() {
        ModelTokenizerModelContextTokenCounter counter =
                ModelTokenizerModelContextTokenCounter.forModel("custom-private-model");

        assertEquals(EncodingType.CL100K_BASE, counter.encodingType());
    }
}
