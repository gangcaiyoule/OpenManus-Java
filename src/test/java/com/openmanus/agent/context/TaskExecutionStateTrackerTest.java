package com.openmanus.agent.context;

import com.openmanus.aiframework.runtime.model.AiChatMessage;
import com.openmanus.aiframework.runtime.model.AiToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskExecutionStateTrackerTest {

    @Test
    void shouldBuildTaskStateFromAssistantPlanAndToolCalls() {
        AiChatMessage assistant = AiChatMessage.assistant(
                "step-1: collect files\nstep-2: summarize",
                List.of(
                        new AiToolCall("id-1", "readFile", "{}"),
                        new AiToolCall("id-2", "readFile", "{}"),
                        new AiToolCall("id-3", "writeFile", "{}")
                )
        );

        TaskExecutionState state = TaskExecutionStateTracker.updateFromAssistantPlan(
                TaskExecutionState.empty(),
                assistant
        );

        assertEquals("step-1: collect files", state.plan());
        assertEquals("readFile", state.inProgress());
        assertEquals(List.of("readFile", "writeFile"), state.todo());
        assertNull(state.lastFailure());
    }

    @Test
    void shouldUpdatePlanWithoutTodoWhenAssistantHasNoToolCalls() {
        TaskExecutionState current = new TaskExecutionState("old-plan", "readFile", List.of("readFile"), "tool=readFile; reason=timeout");

        TaskExecutionState next = TaskExecutionStateTracker.updateFromAssistantPlan(
                current,
                AiChatMessage.assistant("\n\nnew plan\nnext line")
        );

        assertEquals("new plan", next.plan());
        assertEquals("readFile", next.inProgress());
        assertEquals(List.of("readFile"), next.todo());
        assertEquals("tool=readFile; reason=timeout", next.lastFailure());
    }

    @Test
    void shouldIgnoreNonAssistantMessagesWhenUpdatingPlan() {
        TaskExecutionState current = new TaskExecutionState("plan", "readFile", List.of("readFile"), null);

        TaskExecutionState next = TaskExecutionStateTracker.updateFromAssistantPlan(
                current,
                AiChatMessage.user("user message")
        );

        assertEquals(current, next);
    }

    @Test
    void shouldApplyCustomPlanBudgetWhenUpdatingFromAssistantPlan() {
        TaskStateBudgetPolicy policy = new TaskStateBudgetPolicy(8, 120, 240, 6, 120);

        TaskExecutionState next = TaskExecutionStateTracker.updateFromAssistantPlan(
                TaskExecutionState.empty(policy),
                AiChatMessage.assistant("1234567890\nnext", List.of(new AiToolCall("id-1", "readFile", "{}"))),
                policy
        );

        assertEquals("12345678", next.plan());
        assertEquals("readFile", next.inProgress());
        assertEquals(List.of("readFile"), next.todo());
    }

    @Test
    void shouldKeepExistingStateWhenAssistantPlanMessageMissing() {
        TaskExecutionState current = new TaskExecutionState(
                "old-plan",
                "readFile",
                List.of("readFile", "writeFile"),
                "tool=readFile; reason=timeout"
        );

        TaskExecutionState next = TaskExecutionStateTracker.updateFromAssistantPlan(
                current,
                null
        );

        assertEquals("old-plan", next.plan());
        assertEquals("readFile", next.inProgress());
        assertEquals(List.of("readFile", "writeFile"), next.todo());
        assertEquals("tool=readFile; reason=timeout", next.lastFailure());
    }

    @Test
    void shouldKeepExistingPlanWhenAssistantPlanContentIsBlankAndNoToolsProvided() {
        TaskExecutionState current = new TaskExecutionState(
                "old-plan",
                "readFile",
                List.of("readFile", "writeFile"),
                "tool=readFile; reason=timeout"
        );

        TaskExecutionState next = TaskExecutionStateTracker.updateFromAssistantPlan(
                current,
                AiChatMessage.assistant(" \n\t ")
        );

        assertEquals("old-plan", next.plan());
        assertEquals("readFile", next.inProgress());
        assertEquals(List.of("readFile", "writeFile"), next.todo());
        assertEquals("tool=readFile; reason=timeout", next.lastFailure());
    }

    @Test
    void shouldKeepExistingPlanWhenAssistantPlanContentIsBlankButToolCallsExist() {
        TaskExecutionState current = new TaskExecutionState(
                "old-plan",
                "readFile",
                List.of("readFile", "writeFile"),
                "tool=readFile; reason=timeout"
        );

        TaskExecutionState next = TaskExecutionStateTracker.updateFromAssistantPlan(
                current,
                AiChatMessage.assistant(
                        " \n\t ",
                        List.of(new AiToolCall("id-1", "searchWeb", "{}"))
                )
        );

        assertEquals("old-plan", next.plan());
        assertEquals("searchWeb", next.inProgress());
        assertEquals(List.of("searchWeb"), next.todo());
        assertNull(next.lastFailure());
    }

    @Test
    void shouldMarkToolStartedWithoutChangingTodoOrFailure() {
        TaskExecutionState initial = new TaskExecutionState(
                "plan",
                "readFile",
                List.of("readFile", "writeFile"),
                "tool=readFile; reason=timeout"
        );

        TaskExecutionState next = TaskExecutionStateTracker.markToolStarted(initial, "writeFile");

        assertEquals("plan", next.plan());
        assertEquals("writeFile", next.inProgress());
        assertEquals(List.of("readFile", "writeFile"), next.todo());
        assertEquals("tool=readFile; reason=timeout", next.lastFailure());
    }

    @Test
    void shouldNoOpWhenToolNameBlankOnToolStarted() {
        TaskExecutionState initial = new TaskExecutionState("plan", "readFile", List.of("readFile"), null);

        TaskExecutionState next = TaskExecutionStateTracker.markToolStarted(initial, " ");

        assertEquals(initial, next);
    }

    @Test
    void shouldAdvanceTodoAndClearFailureWhenToolSucceeded() {
        TaskExecutionState initial = new TaskExecutionState(
                "plan",
                "readFile",
                List.of("readFile", "writeFile"),
                "tool=readFile; reason=timeout"
        );

        TaskExecutionState next = TaskExecutionStateTracker.markToolSucceeded(initial, "readFile");

        assertEquals("plan", next.plan());
        assertEquals("writeFile", next.inProgress());
        assertEquals(List.of("writeFile"), next.todo());
        assertNull(next.lastFailure());
    }

    @Test
    void shouldKeepTodoOrderWhenSucceededToolIsNotTracked() {
        TaskExecutionState initial = new TaskExecutionState(
                "plan",
                "readFile",
                List.of("readFile", "writeFile"),
                "tool=readFile; reason=timeout"
        );

        TaskExecutionState next = TaskExecutionStateTracker.markToolSucceeded(initial, "searchWeb");

        assertNotSame(initial, next);
        assertEquals("readFile", next.inProgress());
        assertEquals(List.of("readFile", "writeFile"), next.todo());
        assertNull(next.lastFailure());
    }

    @Test
    void shouldRecordFailureWithLengthGuard() {
        String longReason = "x".repeat(300);
        TaskExecutionState initial = new TaskExecutionState("plan", "readFile", List.of("readFile"), null);

        TaskExecutionState next = TaskExecutionStateTracker.markToolFailed(initial, "readFile", longReason);

        assertEquals("readFile", next.inProgress());
        assertTrue(next.lastFailure().startsWith("tool=readFile; reason="));
        assertTrue(next.lastFailure().endsWith("..."));
    }

    @Test
    void shouldUseUnknownToolFallbackWhenToolNameMissing() {
        TaskExecutionState initial = new TaskExecutionState("plan", "readFile", List.of("readFile"), null);

        TaskExecutionState next = TaskExecutionStateTracker.markToolFailed(initial, " ", " ");

        assertEquals("unknown_tool", next.inProgress());
        assertEquals("tool=unknown_tool; reason=unknown error", next.lastFailure());
    }

    @Test
    void shouldTrimFailureToConfiguredBudgetWhenPrefixExceedsRemainingCapacity() {
        TaskStateBudgetPolicy policy = new TaskStateBudgetPolicy(240, 120, 12, 6, 120);
        TaskExecutionState initial = TaskExecutionState.empty(policy);

        TaskExecutionState next = TaskExecutionStateTracker.markToolFailed(
                initial,
                "readFile",
                "timeout while reading file",
                policy
        );

        assertEquals(12, next.lastFailure().length());
        assertEquals("tool=readFil", next.lastFailure());
    }

    @Test
    void shouldApplyCustomFailureBudgetWhenConfigured() {
        TaskStateBudgetPolicy policy = new TaskStateBudgetPolicy(240, 120, 32, 6, 120);
        TaskExecutionState initial = TaskExecutionState.from("plan", "readFile", List.of("readFile"), null, policy);

        TaskExecutionState next = TaskExecutionStateTracker.markToolFailed(
                initial,
                "readFile",
                "x".repeat(120),
                policy
        );

        assertEquals(32, next.lastFailure().length());
        assertTrue(next.lastFailure().startsWith("tool=readFile; reason="));
    }
}
