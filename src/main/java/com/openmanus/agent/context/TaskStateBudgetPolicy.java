package com.openmanus.agent.context;

/**
 * Budget policy for task-state fields rendered into model context.
 */
public final class TaskStateBudgetPolicy {

    public static final int DEFAULT_PLAN_MAX_CHARS = 240;
    public static final int DEFAULT_IN_PROGRESS_MAX_CHARS = 120;
    public static final int DEFAULT_LAST_FAILURE_MAX_CHARS = 240;
    public static final int DEFAULT_TODO_MAX_ITEMS = 6;
    public static final int DEFAULT_TODO_ITEM_MAX_CHARS = 120;

    private final int planMaxChars;
    private final int inProgressMaxChars;
    private final int lastFailureMaxChars;
    private final int todoMaxItems;
    private final int todoItemMaxChars;

    public TaskStateBudgetPolicy(int planMaxChars,
                                 int inProgressMaxChars,
                                 int lastFailureMaxChars,
                                 int todoMaxItems,
                                 int todoItemMaxChars) {
        this.planMaxChars = sanitizePositive(planMaxChars, DEFAULT_PLAN_MAX_CHARS);
        this.inProgressMaxChars = sanitizePositive(inProgressMaxChars, DEFAULT_IN_PROGRESS_MAX_CHARS);
        this.lastFailureMaxChars = sanitizePositive(lastFailureMaxChars, DEFAULT_LAST_FAILURE_MAX_CHARS);
        this.todoMaxItems = sanitizePositive(todoMaxItems, DEFAULT_TODO_MAX_ITEMS);
        this.todoItemMaxChars = sanitizePositive(todoItemMaxChars, DEFAULT_TODO_ITEM_MAX_CHARS);
    }

    public static TaskStateBudgetPolicy defaults() {
        return new TaskStateBudgetPolicy(
                DEFAULT_PLAN_MAX_CHARS,
                DEFAULT_IN_PROGRESS_MAX_CHARS,
                DEFAULT_LAST_FAILURE_MAX_CHARS,
                DEFAULT_TODO_MAX_ITEMS,
                DEFAULT_TODO_ITEM_MAX_CHARS
        );
    }

    public int planMaxChars() {
        return planMaxChars;
    }

    public int inProgressMaxChars() {
        return inProgressMaxChars;
    }

    public int lastFailureMaxChars() {
        return lastFailureMaxChars;
    }

    public int todoMaxItems() {
        return todoMaxItems;
    }

    public int todoItemMaxChars() {
        return todoItemMaxChars;
    }

    private static int sanitizePositive(int value, int fallback) {
        return value <= 0 ? fallback : value;
    }
}
