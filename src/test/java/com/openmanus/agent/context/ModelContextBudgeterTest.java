package com.openmanus.agent.context;

import com.openmanus.aiframework.runtime.model.AiChatMessage;
import com.openmanus.aiframework.runtime.model.AiToolCall;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelContextBudgeterTest {

    @Test
    void shouldReturnInputAsIsWhenBudgetDisabledOrMessagesEmpty() {
        assertEquals(List.of(), ModelContextBudgeter.applyApproxTokenBudget(null, null, 32));

        List<AiChatMessage> empty = List.of();
        assertEquals(empty, ModelContextBudgeter.applyApproxTokenBudget(empty, null, 32));

        List<AiChatMessage> messages = List.of(AiChatMessage.user("hello"));
        assertEquals(messages, ModelContextBudgeter.applyApproxTokenBudget(messages, messages.get(0), 0));
    }

    @Test
    void shouldKeepCriticalAnchorsAndNewestMessagesUnderBudget() {
        AiChatMessage system = AiChatMessage.system("sys");
        AiChatMessage historyUser = AiChatMessage.user("history question");
        AiChatMessage historyAssistant = AiChatMessage.assistant("history answer");
        AiChatMessage currentUser = AiChatMessage.user("current ask");
        AiChatMessage latestTool = new AiChatMessage(AiChatMessage.Role.TOOL, "tool output", "search", "call_1", List.of());
        AiChatMessage assistant = AiChatMessage.assistant("final");

        List<AiChatMessage> messages = List.of(system, historyUser, historyAssistant, currentUser, latestTool, assistant);
        List<AiChatMessage> result = ModelContextBudgeter.applyApproxTokenBudget(messages, currentUser, 200);

        assertTrue(result.contains(system));
        assertTrue(result.contains(currentUser));
        assertTrue(result.contains(latestTool));
        assertTrue(result.contains(assistant));
    }

    @Test
    void shouldFallbackToMinimalCriticalSetWhenFixedMessagesOverflowBudget() {
        AiChatMessage system = AiChatMessage.system("S".repeat(200));
        AiChatMessage currentUser = AiChatMessage.user("U".repeat(160));
        AiChatMessage latestTool = new AiChatMessage(AiChatMessage.Role.TOOL, "T".repeat(120), "search", "call_2", List.of());
        List<AiChatMessage> messages = List.of(system, currentUser, latestTool);

        List<AiChatMessage> result = ModelContextBudgeter.applyApproxTokenBudget(messages, currentUser, 16);

        assertEquals(1, result.size());
        assertSame(currentUser, result.get(0));
    }

    @Test
    void shouldFallbackToTailMessageWhenNoCriticalAnchorsAvailable() {
        AiChatMessage assistant1 = AiChatMessage.assistant("a1");
        AiChatMessage assistant2 = AiChatMessage.assistant("a2");
        List<AiChatMessage> messages = List.of(assistant1, assistant2);

        List<AiChatMessage> result = ModelContextBudgeter.applyApproxTokenBudget(messages, null, 1);

        assertEquals(1, result.size());
        assertSame(assistant2, result.get(0));
    }

    @Test
    void shouldEstimateAssistantTokenWithToolCallsWhenTextIsBlank() {
        AiChatMessage assistantWithToolCall = AiChatMessage.assistant(
                "",
                List.of(new AiToolCall("call_1", "search", "{\"q\":\"weather\"}"))
        );
        AiChatMessage currentUser = AiChatMessage.user("what is weather");
        List<AiChatMessage> messages = List.of(currentUser, assistantWithToolCall);

        List<AiChatMessage> result = ModelContextBudgeter.applyApproxTokenBudget(messages, currentUser, 20);

        assertTrue(result.contains(currentUser));
        assertEquals(1, result.size());
    }

    @Test
    void shouldApplyInjectedTokenCounterWhenBudgeting() {
        AiChatMessage system = AiChatMessage.system("sys");
        AiChatMessage currentUser = AiChatMessage.user("ask");
        AiChatMessage assistant = AiChatMessage.assistant("result");
        List<AiChatMessage> messages = List.of(system, currentUser, assistant);

        ModelContextTokenCounter customCounter = message -> message == currentUser ? 1 : 100;
        List<AiChatMessage> result = ModelContextBudgeter.applyTokenBudget(messages, currentUser, 2, customCounter);

        assertEquals(1, result.size());
        assertSame(currentUser, result.getFirst());
    }

    @Test
    void shouldIgnoreNullMessagesWhenBudgeting() {
        AiChatMessage currentUser = AiChatMessage.user("ask");

        List<AiChatMessage> result = ModelContextBudgeter.applyTokenBudget(
                Arrays.asList(null, currentUser),
                currentUser,
                4,
                message -> 1
        );

        assertEquals(1, result.size());
        assertSame(currentUser, result.getFirst());
    }
}
