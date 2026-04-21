package com.openmanus.agent.context;

import com.openmanus.aiframework.runtime.model.AiChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextAssemblerTest {

    @Test
    void shouldReturnEmptyWhenSnapshotIsNull() {
        ContextAssembler assembler = new ContextAssembler(new ContextBudgetPolicy(0, 0, 0));

        List<AiChatMessage> messages = assembler.assemble(null);

        assertTrue(messages.isEmpty());
    }

    @Test
    void shouldReturnCurrentUserWhenNoFullMessagesProvided() {
        ContextAssembler assembler = new ContextAssembler(new ContextBudgetPolicy(0, 0, 0));
        AiChatMessage currentUser = AiChatMessage.user("new-user");

        List<AiChatMessage> messages = assembler.assemble(
                new ContextSnapshot(List.of(), currentUser, List.of(), List.of())
        );

        assertEquals(1, messages.size());
        assertSame(currentUser, messages.getFirst());
    }

    @Test
    void shouldInjectTaskStateCardWhenNoFullMessagesProvided() {
        ContextAssembler assembler = new ContextAssembler(new ContextBudgetPolicy(0, 2, 0));
        AiChatMessage currentUser = AiChatMessage.user("new-user");
        TaskExecutionState state = new TaskExecutionState(
                "read and summarize",
                "readFile",
                List.of("readFile"),
                null
        );

        List<AiChatMessage> messages = assembler.assemble(
                new ContextSnapshot(List.of(), currentUser, List.of(), List.of()),
                state
        );

        assertEquals(2, messages.size());
        assertSame(currentUser, messages.getFirst());
        assertEquals(AiChatMessage.Role.ASSISTANT, messages.get(1).role());
        assertTrue(messages.get(1).content().contains("[Task State]"));
    }

    @Test
    void shouldKeepCurrentTurnMessagesWhenFullMessagesAreEmpty() {
        ContextAssembler assembler = new ContextAssembler(new ContextBudgetPolicy(0, 3, 0));
        AiChatMessage currentUser = AiChatMessage.user("new-user");
        AiChatMessage tool = new AiChatMessage(
                AiChatMessage.Role.TOOL,
                "tool-result",
                "browser",
                "call_1",
                List.of()
        );

        List<AiChatMessage> messages = assembler.assemble(
                new ContextSnapshot(List.of(), currentUser, List.of(), List.of(currentUser, tool))
        );

        assertEquals(2, messages.size());
        assertSame(currentUser, messages.getFirst());
        assertSame(tool, messages.get(1));
    }

    @Test
    void shouldPrependDetachedCurrentUserWhenCurrentTurnMissesUserAndFullMessagesAreEmpty() {
        ContextAssembler assembler = new ContextAssembler(new ContextBudgetPolicy(0, 3, 0));
        AiChatMessage currentUser = AiChatMessage.user("new-user");
        AiChatMessage tool = new AiChatMessage(
                AiChatMessage.Role.TOOL,
                "tool-result",
                "browser",
                "call_1",
                List.of()
        );

        List<AiChatMessage> messages = assembler.assemble(
                new ContextSnapshot(List.of(), currentUser, List.of(), List.of(tool))
        );

        assertEquals(2, messages.size());
        assertSame(currentUser, messages.getFirst());
        assertSame(tool, messages.get(1));
    }

    @Test
    void shouldAppendCurrentUserAfterTrimmedHistoryWhenCurrentTurnIsEmpty() {
        ContextAssembler assembler = new ContextAssembler(new ContextBudgetPolicy(2, 4, 0));
        AiChatMessage system = AiChatMessage.system("sys");
        AiChatMessage old1 = AiChatMessage.user("old-1");
        AiChatMessage old2 = AiChatMessage.assistant("old-2");
        AiChatMessage currentUser = AiChatMessage.user("new-user");
        ContextSnapshot snapshot = new ContextSnapshot(
                List.of(system, old1, old2),
                currentUser,
                List.of(system, old1, old2),
                List.of()
        );

        List<AiChatMessage> messages = assembler.assemble(snapshot);

        assertEquals(4, messages.size());
        assertSame(system, messages.getFirst());
        assertEquals(AiChatMessage.Role.ASSISTANT, messages.get(1).role());
        assertTrue(messages.get(1).content().contains("[Historical Key Memory]"));
        assertSame(old2, messages.get(2));
        assertSame(currentUser, messages.get(3));
    }

    @Test
    void shouldKeepCurrentTurnMessagesWithHistoryUnderTotalLimit() {
        ContextAssembler assembler = new ContextAssembler(new ContextBudgetPolicy(2, 4, 0));
        AiChatMessage system = AiChatMessage.system("sys");
        AiChatMessage history = AiChatMessage.user("old-user");
        AiChatMessage currentUser = AiChatMessage.user("new-user");
        AiChatMessage tool = new AiChatMessage(AiChatMessage.Role.TOOL, "tool", "search", "call_1", List.of());

        List<AiChatMessage> messages = assembler.assemble(
                ContextSnapshot.from(List.of(system, history, currentUser, tool), currentUser)
        );

        assertEquals(4, messages.size());
        assertSame(system, messages.getFirst());
        assertTrue(messages.contains(currentUser));
        assertTrue(messages.contains(tool));
    }

    @Test
    void shouldKeepDetachedCurrentUserAnchorWhenCurrentTurnOnlyHasToolMessages() {
        ContextAssembler assembler = new ContextAssembler(new ContextBudgetPolicy(2, 3, 0));
        AiChatMessage system = AiChatMessage.system("sys");
        AiChatMessage history = AiChatMessage.assistant("old");
        AiChatMessage currentUser = AiChatMessage.user("new-user");
        AiChatMessage tool = new AiChatMessage(
                AiChatMessage.Role.TOOL,
                "tool",
                "search",
                "call_1",
                List.of()
        );

        List<AiChatMessage> messages = assembler.assemble(
                new ContextSnapshot(
                        List.of(system, history, tool),
                        currentUser,
                        List.of(system, history),
                        List.of(tool)
                )
        );

        assertEquals(3, messages.size());
        assertSame(system, messages.getFirst());
        assertSame(currentUser, messages.get(1));
        assertSame(tool, messages.get(2));
    }

    @Test
    void shouldKeepCurrentUserAnchorWhenTaskStateInjectedUnderTotalLimitOne() {
        ContextAssembler assembler = new ContextAssembler(new ContextBudgetPolicy(0, 1, 0));
        AiChatMessage currentUser = AiChatMessage.user("new-user");
        TaskExecutionState state = new TaskExecutionState(
                "read and summarize",
                "readFile",
                List.of("readFile"),
                null
        );

        List<AiChatMessage> messages = assembler.assemble(
                ContextSnapshot.from(List.of(currentUser), currentUser),
                state
        );

        assertEquals(1, messages.size());
        assertSame(currentUser, messages.getFirst());
        assertFalse(messages.getFirst().content().contains("[Task State]"));
    }

    @Test
    void shouldKeepUserAndAssistantTaskStateCardWhenTotalLimitIsTwo() {
        ContextAssembler assembler = new ContextAssembler(new ContextBudgetPolicy(0, 2, 0));
        AiChatMessage currentUser = AiChatMessage.user("new-user");
        TaskExecutionState state = new TaskExecutionState(
                "read and summarize",
                "readFile",
                List.of("readFile"),
                null
        );

        List<AiChatMessage> messages = assembler.assemble(
                ContextSnapshot.from(List.of(currentUser), currentUser),
                state
        );

        assertEquals(2, messages.size());
        assertSame(currentUser, messages.getFirst());
        assertEquals(AiChatMessage.Role.ASSISTANT, messages.get(1).role());
        assertTrue(messages.get(1).content().contains("[Task State]"));
        assertFalse(messages.stream().anyMatch(message ->
                message.role() == AiChatMessage.Role.SYSTEM
                        && message.content() != null
                        && message.content().contains("[Task State]")));
    }

    @Test
    void shouldKeepPersistedCurrentTurnWhenDetachedCurrentUserHitsTotalLimit() {
        ContextAssembler assembler = new ContextAssembler(new ContextBudgetPolicy(0, 2, 0));
        AiChatMessage system = AiChatMessage.system("sys");
        AiChatMessage history = AiChatMessage.assistant("old");
        AiChatMessage persistedCurrentUser = AiChatMessage.user("same-user-text");
        AiChatMessage latestTool = new AiChatMessage(
                AiChatMessage.Role.TOOL, "tool-result", "browser", "call_1", List.of()
        );
        AiChatMessage detachedCurrentUser = AiChatMessage.user("same-user-text");

        List<AiChatMessage> messages = assembler.assemble(
                ContextSnapshot.from(
                        List.of(system, history, persistedCurrentUser, latestTool),
                        detachedCurrentUser
                )
        );

        assertEquals(2, messages.size());
        assertSame(persistedCurrentUser, messages.getFirst());
        assertSame(latestTool, messages.get(1));
        assertFalse(messages.contains(system));
        assertFalse(messages.contains(history));
    }

    @Test
    void shouldFallbackToDefaultBudgetPolicyWhenAssemblerBudgetMissing() {
        ContextAssembler assembler = new ContextAssembler(null);
        AiChatMessage currentUser = AiChatMessage.user("new-user");

        List<AiChatMessage> messages = assembler.assemble(
                new ContextSnapshot(List.of(currentUser), currentUser, List.of(), List.of(currentUser))
        );

        assertEquals(1, messages.size());
        assertSame(currentUser, messages.getFirst());
    }

    @Test
    void shouldInjectHistoricalKeyMemoryCardBeforeRetainedHistoryWhenHistoryTrimmed() {
        ContextAssembler assembler = new ContextAssembler(new ContextBudgetPolicy(2, 5, 0));
        AiChatMessage system = AiChatMessage.system("sys");
        AiChatMessage oldUser = AiChatMessage.user("find weather");
        AiChatMessage oldAssistant = AiChatMessage.assistant("searching weather");
        AiChatMessage retainedAssistant = AiChatMessage.assistant("latest answer");
        AiChatMessage currentUser = AiChatMessage.user("new-user");

        List<AiChatMessage> messages = assembler.assemble(
                ContextSnapshot.from(
                        List.of(system, oldUser, oldAssistant, retainedAssistant, currentUser),
                        currentUser
                )
        );

        assertEquals(4, messages.size());
        assertSame(system, messages.getFirst());
        assertEquals(AiChatMessage.Role.ASSISTANT, messages.get(1).role());
        assertTrue(messages.get(1).content().contains("[Historical Key Memory]"));
        assertTrue(messages.get(1).content().contains("lastUserIntent: find weather"));
        assertTrue(messages.get(1).content().contains("lastAssistantOutcome: searching weather"));
        assertSame(retainedAssistant, messages.get(2));
        assertSame(currentUser, messages.get(3));
    }
}
