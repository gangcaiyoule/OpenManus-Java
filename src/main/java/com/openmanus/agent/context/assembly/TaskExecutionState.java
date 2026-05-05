package com.openmanus.agent.context.assembly;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Minimal task-state carrier for one execute loop.
 */
public final class TaskExecutionState {

    static final int PLAN_MAX_CHARS = Budget.DEFAULT_PLAN_MAX_CHARS;
    static final int IN_PROGRESS_MAX_CHARS = Budget.DEFAULT_IN_PROGRESS_MAX_CHARS;
    static final int LAST_FAILURE_MAX_CHARS = Budget.DEFAULT_LAST_FAILURE_MAX_CHARS;
    static final int TODO_MAX_ITEMS = Budget.DEFAULT_TODO_MAX_ITEMS;
    static final int TODO_ITEM_MAX_CHARS = Budget.DEFAULT_TODO_ITEM_MAX_CHARS;
    private static final Budget DEFAULT_BUDGET = Budget.defaults();

    private final String plan;
    private final String inProgress;
    private final List<String> todo;
    private final String lastFailure;

    public TaskExecutionState(String plan, String inProgress, List<String> todo, String lastFailure) {
        this(plan, inProgress, todo, lastFailure, DEFAULT_BUDGET);
    }

    public static TaskExecutionState from(String plan,
                                          String inProgress,
                                          List<String> todo,
                                          String lastFailure,
                                          Budget budget) {
        return new TaskExecutionState(plan, inProgress, todo, lastFailure, budget);
    }

    private TaskExecutionState(String plan,
                               String inProgress,
                               List<String> todo,
                               String lastFailure,
                               Budget budget) {
        Budget effectiveBudget = budget == null ? DEFAULT_BUDGET : budget;
        this.plan = normalize(plan, effectiveBudget.planMaxChars());
        this.inProgress = normalize(inProgress, effectiveBudget.inProgressMaxChars());
        this.lastFailure = normalize(lastFailure, effectiveBudget.lastFailureMaxChars());
        this.todo = normalizeTodo(todo, effectiveBudget.todoMaxItems(), effectiveBudget.todoItemMaxChars());
    }

    public static TaskExecutionState empty() {
        return new TaskExecutionState(null, null, List.of(), null);
    }

    public static TaskExecutionState empty(Budget budget) {
        return from(null, null, List.of(), null, budget);
    }

    public boolean isEmpty() {
        return plan == null
                && inProgress == null
                && (todo == null || todo.isEmpty())
                && lastFailure == null;
    }

    public TaskExecutionState withPlan(String nextPlan) {
        return new TaskExecutionState(nextPlan, inProgress, todo, lastFailure);
    }

    public TaskExecutionState withPlan(String nextPlan, Budget budget) {
        return from(nextPlan, inProgress, todo, lastFailure, budget);
    }

    public TaskExecutionState withInProgress(String nextInProgress) {
        return new TaskExecutionState(plan, nextInProgress, todo, lastFailure);
    }

    public TaskExecutionState withInProgress(String nextInProgress, Budget budget) {
        return from(plan, nextInProgress, todo, lastFailure, budget);
    }

    public TaskExecutionState withTodo(List<String> nextTodo) {
        return new TaskExecutionState(plan, inProgress, nextTodo, lastFailure);
    }

    public TaskExecutionState withTodo(List<String> nextTodo, Budget budget) {
        return from(plan, inProgress, nextTodo, lastFailure, budget);
    }

    public TaskExecutionState withLastFailure(String nextLastFailure) {
        return new TaskExecutionState(plan, inProgress, todo, nextLastFailure);
    }

    public TaskExecutionState withLastFailure(String nextLastFailure, Budget budget) {
        return from(plan, inProgress, todo, nextLastFailure, budget);
    }

    public String plan() {
        return plan;
    }

    public String inProgress() {
        return inProgress;
    }

    public List<String> todo() {
        return todo;
    }

    public String lastFailure() {
        return lastFailure;
    }

    /**
     * Budget for task-state fields rendered into model context.
     */
    public record Budget(int planMaxChars,
                         int inProgressMaxChars,
                         int lastFailureMaxChars,
                         int todoMaxItems,
                         int todoItemMaxChars) {

        public static final int DEFAULT_PLAN_MAX_CHARS = 240;
        public static final int DEFAULT_IN_PROGRESS_MAX_CHARS = 120;
        public static final int DEFAULT_LAST_FAILURE_MAX_CHARS = 240;
        public static final int DEFAULT_TODO_MAX_ITEMS = 6;
        public static final int DEFAULT_TODO_ITEM_MAX_CHARS = 120;

        public Budget {
            planMaxChars = sanitizePositive(planMaxChars, DEFAULT_PLAN_MAX_CHARS);
            inProgressMaxChars = sanitizePositive(inProgressMaxChars, DEFAULT_IN_PROGRESS_MAX_CHARS);
            lastFailureMaxChars = sanitizePositive(lastFailureMaxChars, DEFAULT_LAST_FAILURE_MAX_CHARS);
            todoMaxItems = sanitizePositive(todoMaxItems, DEFAULT_TODO_MAX_ITEMS);
            todoItemMaxChars = sanitizePositive(todoItemMaxChars, DEFAULT_TODO_ITEM_MAX_CHARS);
        }

        public static Budget defaults() {
            return new Budget(
                    DEFAULT_PLAN_MAX_CHARS,
                    DEFAULT_IN_PROGRESS_MAX_CHARS,
                    DEFAULT_LAST_FAILURE_MAX_CHARS,
                    DEFAULT_TODO_MAX_ITEMS,
                    DEFAULT_TODO_ITEM_MAX_CHARS
            );
        }

        private static int sanitizePositive(int value, int fallback) {
            return value <= 0 ? fallback : value;
        }
    }

    private static String normalize(String value, int maxChars) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= maxChars ? trimmed : trimmed.substring(0, maxChars);
    }

    private static List<String> normalizeTodo(List<String> values, int maxItems, int itemMaxChars) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            if (normalized.size() >= maxItems) {
                break;
            }
            String trimmed = normalize(value, itemMaxChars);
            if (trimmed != null) {
                normalized.add(trimmed);
            }
        }
        return List.copyOf(normalized);
    }

    @Override
    public String toString() {
        return "TaskExecutionState{"
                + "plan='" + plan + '\''
                + ", inProgress='" + inProgress + '\''
                + ", todo=" + todo
                + ", lastFailure='" + lastFailure + '\''
                + '}';
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TaskExecutionState that)) {
            return false;
        }
        return Objects.equals(plan, that.plan)
                && Objects.equals(inProgress, that.inProgress)
                && Objects.equals(todo, that.todo)
                && Objects.equals(lastFailure, that.lastFailure);
    }

    @Override
    public int hashCode() {
        return Objects.hash(plan, inProgress, todo, lastFailure);
    }
}
