package com.openmanus.agent.context;

import com.openmanus.aiframework.runtime.model.AiChatMessage;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextBudgetPolicyTest {

    @Test
    void shouldKeepFirstSystemAndLatestNonSystemWhenTrimmingHistory() {
        ContextBudgetPolicy policy = new ContextBudgetPolicy(2, 0, 0);
        AiChatMessage system = AiChatMessage.system("sys");
        AiChatMessage user1 = AiChatMessage.user("u1");
        AiChatMessage user2 = AiChatMessage.user("u2");

        List<AiChatMessage> trimmed = policy.trimHistory(List.of(system, user1, user2));

        assertEquals(2, trimmed.size());
        assertSame(system, trimmed.getFirst());
        assertSame(user2, trimmed.get(1));
    }

    @Test
    void shouldKeepCurrentUserWhenTotalLimitIsOne() {
        ContextBudgetPolicy policy = new ContextBudgetPolicy(0, 1, 0);
        AiChatMessage system = AiChatMessage.system("sys");
        AiChatMessage currentUser = AiChatMessage.user("new-user");

        List<AiChatMessage> trimmed = policy.trimForTotalLimit(List.of(system, currentUser), currentUser);

        assertEquals(1, trimmed.size());
        assertSame(currentUser, trimmed.getFirst());
    }

    @Test
    void shouldPrioritizeLatestToolWhenTotalLimitIsTwo() {
        ContextBudgetPolicy policy = new ContextBudgetPolicy(0, 2, 0);
        AiChatMessage system = AiChatMessage.system("sys");
        AiChatMessage currentUser = AiChatMessage.user("new-user");
        AiChatMessage latestTool = new AiChatMessage(
                AiChatMessage.Role.TOOL, "result", "search", "call_1", List.of()
        );

        List<AiChatMessage> trimmed = policy.trimForTotalLimit(
                List.of(system, currentUser, latestTool),
                currentUser
        );

        assertEquals(2, trimmed.size());
        assertTrue(trimmed.contains(currentUser));
        assertTrue(trimmed.contains(latestTool));
        assertFalse(trimmed.contains(system));
    }

    @Test
    void shouldKeepPersistedCurrentUserWhenDetachedCurrentUserHitsTotalLimit() {
        ContextBudgetPolicy policy = new ContextBudgetPolicy(0, 2, 0);
        AiChatMessage system = AiChatMessage.system("sys");
        AiChatMessage history = AiChatMessage.assistant("old");
        AiChatMessage persistedCurrentUser = AiChatMessage.user("same-user-text");
        AiChatMessage latestTool = new AiChatMessage(
                AiChatMessage.Role.TOOL, "result", "search", "call_1", List.of()
        );
        AiChatMessage detachedCurrentUser = AiChatMessage.user("same-user-text");

        List<AiChatMessage> trimmed = policy.trimForTotalLimit(
                List.of(system, history, persistedCurrentUser, latestTool),
                detachedCurrentUser
        );

        assertEquals(2, trimmed.size());
        assertSame(persistedCurrentUser, trimmed.getFirst());
        assertSame(latestTool, trimmed.get(1));
        assertFalse(trimmed.contains(system));
        assertFalse(trimmed.contains(history));
    }

    @Test
    void shouldFallbackToTailMessageWhenApproxTokenBudgetDropsAllMessages() {
        ContextBudgetPolicy policy = new ContextBudgetPolicy(0, 0, 1);
        AiChatMessage assistant1 = AiChatMessage.assistant("a1");
        AiChatMessage assistant2 = AiChatMessage.assistant("a2");

        List<AiChatMessage> trimmed = policy.applyApproxTokenBudget(List.of(assistant1, assistant2), null);

        assertEquals(1, trimmed.size());
        assertSame(assistant2, trimmed.getFirst());
    }

    @Test
    void shouldUseInjectedTokenCounterForBudgeting() {
        AiChatMessage system = AiChatMessage.system("sys");
        AiChatMessage currentUser = AiChatMessage.user("ask");
        AiChatMessage assistant = AiChatMessage.assistant("answer");
        ModelContextTokenCounter counter = message -> message == currentUser ? 1 : 100;
        ContextBudgetPolicy policy = new ContextBudgetPolicy(0, 0, 2, counter);

        List<AiChatMessage> trimmed = policy.applyApproxTokenBudget(List.of(system, currentUser, assistant), currentUser);

        assertEquals(1, trimmed.size());
        assertSame(currentUser, trimmed.getFirst());
    }

    @Test
    void shouldDropNullMessagesBeforeApplyingHistoryAndTotalBudgets() {
        ContextBudgetPolicy policy = new ContextBudgetPolicy(2, 2, 0);
        AiChatMessage system = AiChatMessage.system("sys");
        AiChatMessage currentUser = AiChatMessage.user("ask");
        AiChatMessage latestTool = new AiChatMessage(
                AiChatMessage.Role.TOOL, "result", "search", "call_1", List.of()
        );

        List<AiChatMessage> history = policy.trimHistory(Arrays.asList(system, null, currentUser));
        List<AiChatMessage> total = policy.trimForTotalLimit(
                Arrays.asList(system, null, currentUser, latestTool),
                currentUser
        );

        assertEquals(List.of(system, currentUser), history);
        assertEquals(2, total.size());
        assertSame(currentUser, total.getFirst());
        assertSame(latestTool, total.get(1));
    }

    @Test
    void shouldDropNullMessagesBeforeApplyingTokenBudget() {
        ContextBudgetPolicy policy = new ContextBudgetPolicy(0, 0, 2, message -> message.role() == AiChatMessage.Role.USER ? 1 : 100);
        AiChatMessage currentUser = AiChatMessage.user("ask");

        List<AiChatMessage> trimmed = policy.applyApproxTokenBudget(Arrays.asList(null, currentUser), currentUser);

        assertEquals(1, trimmed.size());
        assertSame(currentUser, trimmed.getFirst());
    }
}
