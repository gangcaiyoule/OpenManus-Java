package com.openmanus.agent.context;

import com.openmanus.aiframework.runtime.model.AiChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ContextSnapshotTest {

    @Test
    void shouldSplitCurrentTurnWhenCurrentUserIsEqualButNotSameInstance() {
        AiChatMessage system = AiChatMessage.system("sys");
        AiChatMessage history = AiChatMessage.assistant("old");
        AiChatMessage persistedCurrentUser = AiChatMessage.user("same-user-text");
        AiChatMessage tool = new AiChatMessage(AiChatMessage.Role.TOOL, "tool-result", "browser", "call_1", List.of());
        AiChatMessage detachedCurrentUser = AiChatMessage.user("same-user-text");

        ContextSnapshot snapshot = ContextSnapshot.from(
                List.of(system, history, persistedCurrentUser, tool),
                detachedCurrentUser
        );

        assertEquals(List.of(system, history), snapshot.historicalMessages());
        assertEquals(2, snapshot.currentTurnMessages().size());
        assertSame(persistedCurrentUser, snapshot.currentTurnMessages().getFirst());
        assertSame(tool, snapshot.currentTurnMessages().get(1));
    }

    @Test
    void shouldPreferLatestEqualCurrentUserMatch() {
        AiChatMessage firstUser = AiChatMessage.user("repeat");
        AiChatMessage assistant = AiChatMessage.assistant("middle");
        AiChatMessage latestUser = AiChatMessage.user("repeat");
        AiChatMessage detachedCurrentUser = AiChatMessage.user("repeat");

        ContextSnapshot snapshot = ContextSnapshot.from(
                List.of(firstUser, assistant, latestUser),
                detachedCurrentUser
        );

        assertEquals(List.of(firstUser, assistant), snapshot.historicalMessages());
        assertEquals(1, snapshot.currentTurnMessages().size());
        assertSame(latestUser, snapshot.currentTurnMessages().getFirst());
    }
}
