package com.openmanus.agent.context;

import com.openmanus.aiframework.runtime.model.AiChatMessage;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskStateContextInjectorTest {

    @Test
    void shouldInjectTaskStateCardWhenStateIsNotEmpty() {
        TaskStateContextInjector injector = new TaskStateContextInjector();
        ContextBudgetPolicy policy = new ContextBudgetPolicy(0, 10, 0);
        TaskExecutionState state = new TaskExecutionState(
                "read and summarize",
                "readFile",
                List.of("readFile", "summarize"),
                null
        );

        List<AiChatMessage> result = injector.inject(
                List.of(AiChatMessage.user("hello")),
                state,
                AiChatMessage.user("hello"),
                policy
        );

        assertEquals(2, result.size());
        AiChatMessage taskCard = result.get(1);
        assertEquals(AiChatMessage.Role.ASSISTANT, taskCard.role());
        assertTrue(taskCard.content().contains("[Task State]"));
        assertTrue(taskCard.content().contains("plan: read and summarize"));
        assertTrue(taskCard.content().contains("- readFile"));
    }

    @Test
    void shouldNotInjectFailureAsSystemRoleWhenLastFailureExists() {
        TaskStateContextInjector injector = new TaskStateContextInjector();
        ContextBudgetPolicy policy = new ContextBudgetPolicy(0, 10, 0);
        TaskExecutionState state = new TaskExecutionState(
                "run diagnostics",
                "invokeTool",
                List.of("invokeTool"),
                "tool=missingTool; reason=Tool not found: missingTool"
        );

        List<AiChatMessage> result = injector.inject(
                List.of(AiChatMessage.user("run diagnostics")),
                state,
                AiChatMessage.user("run diagnostics"),
                policy
        );

        assertEquals(2, result.size());
        AiChatMessage taskCard = result.get(1);
        assertEquals(AiChatMessage.Role.ASSISTANT, taskCard.role());
        assertTrue(taskCard.content().contains("lastFailure: tool=missingTool; reason=Tool not found: missingTool"));
        assertFalse(result.stream().anyMatch(message ->
                        message.role() == AiChatMessage.Role.SYSTEM
                                && message.content() != null
                                && message.content().contains("lastFailure:")),
                "失败信息不应以 SYSTEM 角色注入");
    }

    @Test
    void shouldKeepMessagesUnchangedWhenStateIsEmpty() {
        TaskStateContextInjector injector = new TaskStateContextInjector();
        List<AiChatMessage> base = List.of(AiChatMessage.user("hello"), AiChatMessage.assistant("ok"));

        List<AiChatMessage> result = injector.inject(
                base,
                TaskExecutionState.empty(),
                AiChatMessage.user("hello"),
                new ContextBudgetPolicy(0, 10, 0)
        );

        assertEquals(base, result);
    }

    @Test
    void shouldReturnEmptyWhenBaseMessagesEmpty() {
        TaskStateContextInjector injector = new TaskStateContextInjector();

        List<AiChatMessage> result = injector.inject(
                List.of(),
                new TaskExecutionState("plan", "readFile", List.of("readFile"), null),
                AiChatMessage.user("hello"),
                new ContextBudgetPolicy(0, 10, 0)
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenBaseMessagesMissing() {
        TaskStateContextInjector injector = new TaskStateContextInjector();

        List<AiChatMessage> result = injector.inject(
                null,
                new TaskExecutionState("plan", "readFile", List.of("readFile"), null),
                AiChatMessage.user("hello"),
                new ContextBudgetPolicy(0, 10, 0)
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldAppendTaskStateCardWithoutBudgetTrimWhenPolicyMissing() {
        TaskStateContextInjector injector = new TaskStateContextInjector();
        List<AiChatMessage> result = injector.inject(
                List.of(AiChatMessage.user("run")),
                new TaskExecutionState("plan", "readFile", List.of("readFile"), null),
                AiChatMessage.user("run"),
                null
        );

        assertEquals(2, result.size());
        assertEquals(AiChatMessage.Role.ASSISTANT, result.get(1).role());
        assertTrue(result.get(1).content().contains("[Task State]"));
    }

    @Test
    void shouldRenderTaskStateCardWithBoundedFields() {
        TaskStateContextInjector injector = new TaskStateContextInjector();
        TaskExecutionState state = new TaskExecutionState(
                "p".repeat(500),
                "i".repeat(300),
                List.of(
                        "a".repeat(300),
                        "todo-2",
                        "todo-3",
                        "todo-4",
                        "todo-5",
                        "todo-6",
                        "todo-7"
                ),
                "f".repeat(500)
        );

        List<AiChatMessage> result = injector.inject(
                List.of(AiChatMessage.user("run")),
                state,
                AiChatMessage.user("run"),
                new ContextBudgetPolicy(0, 10, 0)
        );

        assertEquals(2, result.size());
        String card = result.get(1).content();
        assertTrue(card.contains("plan: " + "p".repeat(TaskExecutionState.PLAN_MAX_CHARS)));
        assertTrue(card.contains("inProgress: " + "i".repeat(TaskExecutionState.IN_PROGRESS_MAX_CHARS)));
        assertTrue(card.contains("- " + "a".repeat(TaskExecutionState.TODO_ITEM_MAX_CHARS)));
        assertFalse(card.contains("todo-7"), "todo 超出上限项不应进入上下文卡片");
        assertTrue(card.contains("lastFailure: " + "f".repeat(TaskExecutionState.LAST_FAILURE_MAX_CHARS)));
    }

    @Test
    void shouldRenderTaskStateCardByCustomBudgetPolicy() {
        TaskStateContextInjector injector = new TaskStateContextInjector(
                new TaskStateBudgetPolicy(16, 8, 14, 2, 6)
        );
        TaskExecutionState state = new TaskExecutionState(
                "plan-1234567890-long",
                "inprogress-too-long",
                List.of("todo-item-1", "todo-item-2", "todo-item-3"),
                "failure-too-long"
        );

        List<AiChatMessage> result = injector.inject(
                List.of(AiChatMessage.user("run")),
                state,
                AiChatMessage.user("run"),
                new ContextBudgetPolicy(0, 10, 0)
        );

        String card = result.get(1).content();
        assertTrue(card.contains("plan: plan-1234567890-"));
        assertTrue(card.contains("inProgress: inprogre"));
        assertTrue(card.contains("- todo-i"));
        assertFalse(card.contains("todo-item-3"));
        assertTrue(card.contains("lastFailure: failure-too-lo"));
    }

    @Test
    void shouldRenderFallbackMarkersForMissingTaskStateFields() {
        TaskStateContextInjector injector = new TaskStateContextInjector();

        List<AiChatMessage> result = injector.inject(
                List.of(AiChatMessage.user("run")),
                new TaskExecutionState("plan", " ", Arrays.asList(" ", null), " "),
                AiChatMessage.user("run"),
                new ContextBudgetPolicy(0, 10, 0)
        );

        String card = result.get(1).content();
        assertTrue(card.contains("plan: plan"));
        assertTrue(card.contains("inProgress: n/a"));
        assertTrue(card.contains("todo:\n- n/a"));
        assertTrue(card.contains("lastFailure: n/a"));
    }
}
