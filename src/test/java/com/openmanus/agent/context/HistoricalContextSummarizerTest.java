package com.openmanus.agent.context;

import com.openmanus.aiframework.runtime.model.AiChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoricalContextSummarizerTest {

    @Test
    void shouldInjectSummaryCardAfterLeadingSystemWhenHistoryWasTrimmed() {
        HistoricalContextSummarizer summarizer = new HistoricalContextSummarizer();
        AiChatMessage system = AiChatMessage.system("sys");
        AiChatMessage user1 = AiChatMessage.user("find weather in Shanghai");
        AiChatMessage assistant1 = AiChatMessage.assistant("I will search weather and summarize it.");
        AiChatMessage user2 = AiChatMessage.user("also check humidity");
        AiChatMessage retainedAssistant = AiChatMessage.assistant("latest answer");

        List<AiChatMessage> injected = summarizer.inject(
                List.of(system, user1, assistant1, user2, retainedAssistant),
                List.of(system, retainedAssistant)
        );

        assertEquals(3, injected.size());
        assertSame(system, injected.getFirst());
        assertEquals(AiChatMessage.Role.ASSISTANT, injected.get(1).role());
        assertTrue(injected.get(1).content().contains("[Historical Key Memory]"));
        assertTrue(injected.get(1).content().contains("lastUserIntent: also check humidity"));
        assertTrue(injected.get(1).content().contains("lastAssistantOutcome: I will search weather and summarize it."));
        assertSame(retainedAssistant, injected.get(2));
    }

    @Test
    void shouldSummarizeDroppedToolContextCards() {
        String compressedCard = """
                [Tool Result Context Compressed]
                tool=browser
                artifactId=sha256:test-1
                preview=weather report summary
                """;
        String rehydratedCard = """
                [Tool Result Rehydrated]
                tool=search
                artifactId=sha256:test-2
                payload=humidity details
                """;

        String rendered = HistoricalContextSummarizer.render(List.of(
                AiChatMessage.assistant(compressedCard),
                AiChatMessage.assistant(rehydratedCard)
        ));

        assertTrue(rendered.contains("tool=browser"));
        assertTrue(rendered.contains("artifactId=sha256:test-1"));
        assertTrue(rendered.contains("tool=search"));
        assertTrue(rendered.contains("artifactId=sha256:test-2"));
    }

    @Test
    void shouldReturnRetainedHistoryWhenNothingWasDropped() {
        HistoricalContextSummarizer summarizer = new HistoricalContextSummarizer();
        AiChatMessage system = AiChatMessage.system("sys");
        AiChatMessage assistant = AiChatMessage.assistant("ok");
        List<AiChatMessage> retained = List.of(system, assistant);

        List<AiChatMessage> injected = summarizer.inject(retained, retained);

        assertEquals(retained, injected);
        assertFalse(injected.stream().anyMatch(message ->
                message.role() == AiChatMessage.Role.ASSISTANT
                        && message.content().contains("[Historical Key Memory]")));
    }
}
