package com.openmanus.agent.context;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Minimal task-state carrier for one execute loop.
 */
public final class TaskExecutionState {

    static final int PLAN_MAX_CHARS = TaskStateBudgetPolicy.DEFAULT_PLAN_MAX_CHARS;
    static final int IN_PROGRESS_MAX_CHARS = TaskStateBudgetPolicy.DEFAULT_IN_PROGRESS_MAX_CHARS;
    static final int LAST_FAILURE_MAX_CHARS = TaskStateBudgetPolicy.DEFAULT_LAST_FAILURE_MAX_CHARS;
    static final int TODO_MAX_ITEMS = TaskStateBudgetPolicy.DEFAULT_TODO_MAX_ITEMS;
    static final int TODO_ITEM_MAX_CHARS = TaskStateBudgetPolicy.DEFAULT_TODO_ITEM_MAX_CHARS;
    private static final TaskStateBudgetPolicy DEFAULT_POLICY = TaskStateBudgetPolicy.defaults();

    private final String plan;
    private final String inProgress;
    private final List<String> todo;
    private final String lastFailure;

    public TaskExecutionState(String plan, String inProgress, List<String> todo, String lastFailure) {
        this(plan, inProgress, todo, lastFailure, DEFAULT_POLICY);
    }

    public static TaskExecutionState from(String plan,
                                          String inProgress,
                                          List<String> todo,
                                          String lastFailure,
                                          TaskStateBudgetPolicy budgetPolicy) {
        return new TaskExecutionState(plan, inProgress, todo, lastFailure, budgetPolicy);
    }

    private TaskExecutionState(String plan,
                               String inProgress,
                               List<String> todo,
                               String lastFailure,
                               TaskStateBudgetPolicy budgetPolicy) {
        TaskStateBudgetPolicy policy = budgetPolicy == null ? DEFAULT_POLICY : budgetPolicy;
        this.plan = normalize(plan, policy.planMaxChars());
        this.inProgress = normalize(inProgress, policy.inProgressMaxChars());
        this.lastFailure = normalize(lastFailure, policy.lastFailureMaxChars());
        this.todo = normalizeTodo(todo, policy.todoMaxItems(), policy.todoItemMaxChars());
    }

    public static TaskExecutionState empty() {
        return new TaskExecutionState(null, null, List.of(), null);
    }

    public static TaskExecutionState empty(TaskStateBudgetPolicy budgetPolicy) {
        return from(null, null, List.of(), null, budgetPolicy);
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

    public TaskExecutionState withPlan(String nextPlan, TaskStateBudgetPolicy budgetPolicy) {
        return from(nextPlan, inProgress, todo, lastFailure, budgetPolicy);
    }

    public TaskExecutionState withInProgress(String nextInProgress) {
        return new TaskExecutionState(plan, nextInProgress, todo, lastFailure);
    }

    public TaskExecutionState withInProgress(String nextInProgress, TaskStateBudgetPolicy budgetPolicy) {
        return from(plan, nextInProgress, todo, lastFailure, budgetPolicy);
    }

    public TaskExecutionState withTodo(List<String> nextTodo) {
        return new TaskExecutionState(plan, inProgress, nextTodo, lastFailure);
    }

    public TaskExecutionState withTodo(List<String> nextTodo, TaskStateBudgetPolicy budgetPolicy) {
        return from(plan, inProgress, nextTodo, lastFailure, budgetPolicy);
    }

    public TaskExecutionState withLastFailure(String nextLastFailure) {
        return new TaskExecutionState(plan, inProgress, todo, nextLastFailure);
    }

    public TaskExecutionState withLastFailure(String nextLastFailure, TaskStateBudgetPolicy budgetPolicy) {
        return from(plan, inProgress, todo, nextLastFailure, budgetPolicy);
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
