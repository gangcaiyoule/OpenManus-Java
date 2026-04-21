package com.openmanus.agent.context;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskExecutionStateTest {

    @Test
    void shouldNormalizeAndTrimFieldsWithinBudget() {
        TaskExecutionState state = new TaskExecutionState(
                "  " + "p".repeat(400) + "  ",
                "  " + "i".repeat(200) + "  ",
                new ArrayList<>(List.of("  ", "a".repeat(200), "todo-2", "todo-3", "todo-4", "todo-5", "todo-6", "todo-7")),
                "  " + "f".repeat(400) + "  "
        );

        assertEquals(TaskExecutionState.PLAN_MAX_CHARS, state.plan().length());
        assertEquals(TaskExecutionState.IN_PROGRESS_MAX_CHARS, state.inProgress().length());
        assertEquals(TaskExecutionState.LAST_FAILURE_MAX_CHARS, state.lastFailure().length());
        assertEquals(TaskExecutionState.TODO_MAX_ITEMS, state.todo().size());
        assertEquals(TaskExecutionState.TODO_ITEM_MAX_CHARS, state.todo().getFirst().length());
        assertEquals("todo-2", state.todo().get(1));
    }

    @Test
    void shouldTreatBlankInputsAsEmptyState() {
        List<String> todo = new ArrayList<>();
        todo.add(" ");
        todo.add(null);
        todo.add("\n\t");
        TaskExecutionState state = new TaskExecutionState(" ", " ", todo, " ");
        assertTrue(state.isEmpty());
        assertTrue(state.todo().isEmpty());
    }

    @Test
    void shouldApplyInjectedBudgetPolicyWhenConfigured() {
        TaskStateBudgetPolicy policy = new TaskStateBudgetPolicy(320, 200, 180, 3, 16);
        TaskExecutionState state = TaskExecutionState.from(
                "p".repeat(400),
                "i".repeat(300),
                List.of("12345678901234567890", "todo-2", "todo-3", "todo-4"),
                "f".repeat(300),
                policy
        );

        assertEquals(320, state.plan().length());
        assertEquals(200, state.inProgress().length());
        assertEquals(180, state.lastFailure().length());
        assertEquals(3, state.todo().size());
        assertEquals(16, state.todo().getFirst().length());
    }
}
