package com.openmanus.agent.context;

import com.openmanus.infra.config.OpenManusProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskStateBudgetPolicyTest {

    @Test
    void shouldUseDefaultsWhenConfiguredValuesAreNonPositive() {
        TaskStateBudgetPolicy policy = new TaskStateBudgetPolicy(0, -1, 0, -2, 0);

        assertEquals(TaskStateBudgetPolicy.DEFAULT_PLAN_MAX_CHARS, policy.planMaxChars());
        assertEquals(TaskStateBudgetPolicy.DEFAULT_IN_PROGRESS_MAX_CHARS, policy.inProgressMaxChars());
        assertEquals(TaskStateBudgetPolicy.DEFAULT_LAST_FAILURE_MAX_CHARS, policy.lastFailureMaxChars());
        assertEquals(TaskStateBudgetPolicy.DEFAULT_TODO_MAX_ITEMS, policy.todoMaxItems());
        assertEquals(TaskStateBudgetPolicy.DEFAULT_TODO_ITEM_MAX_CHARS, policy.todoItemMaxChars());
    }

    @Test
    void shouldKeepDefaultValuesConsistentWithChatMemoryConfig() {
        TaskStateBudgetPolicy policy = TaskStateBudgetPolicy.defaults();
        OpenManusProperties.ChatMemoryConfig chatMemoryConfig = new OpenManusProperties.ChatMemoryConfig();

        assertEquals(chatMemoryConfig.getTaskStatePlanMaxChars(), policy.planMaxChars());
        assertEquals(chatMemoryConfig.getTaskStateInProgressMaxChars(), policy.inProgressMaxChars());
        assertEquals(chatMemoryConfig.getTaskStateLastFailureMaxChars(), policy.lastFailureMaxChars());
        assertEquals(chatMemoryConfig.getTaskStateTodoMaxItems(), policy.todoMaxItems());
        assertEquals(chatMemoryConfig.getTaskStateTodoItemMaxChars(), policy.todoItemMaxChars());
    }
}
