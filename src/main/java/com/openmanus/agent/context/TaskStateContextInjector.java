package com.openmanus.agent.context;

import com.openmanus.aiframework.runtime.model.AiChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Injects task-state card into model context as a stable structured block.
 */
public final class TaskStateContextInjector {

    private final TaskStateBudgetPolicy taskStateBudgetPolicy;

    public TaskStateContextInjector() {
        this(TaskStateBudgetPolicy.defaults());
    }

    public TaskStateContextInjector(TaskStateBudgetPolicy taskStateBudgetPolicy) {
        this.taskStateBudgetPolicy = taskStateBudgetPolicy == null
                ? TaskStateBudgetPolicy.defaults()
                : taskStateBudgetPolicy;
    }

    public List<AiChatMessage> inject(List<AiChatMessage> baseMessages,
                                      TaskExecutionState state,
                                      AiChatMessage currentUserMessage,
                                      ContextBudgetPolicy budgetPolicy) {
        if (baseMessages == null || baseMessages.isEmpty()) {
            return List.of();
        }
        if (state == null || state.isEmpty()) {
            return new ArrayList<>(baseMessages);
        }
        TaskExecutionState normalizedState = TaskExecutionState.from(
                state.plan(),
                state.inProgress(),
                state.todo(),
                state.lastFailure(),
                taskStateBudgetPolicy
        );
        List<AiChatMessage> enriched = new ArrayList<>(baseMessages);
        enriched.add(AiChatMessage.assistant(render(normalizedState)));
        if (budgetPolicy == null) {
            return enriched;
        }
        return budgetPolicy.trimForTotalLimit(enriched, currentUserMessage);
    }

    static String render(TaskExecutionState state) {
        String todoBlock = state.todo() == null || state.todo().isEmpty()
                ? "- n/a"
                : state.todo().stream().map(item -> "- " + item).reduce((a, b) -> a + "\n" + b).orElse("- n/a");
        return """
                [Task State]
                plan: %s
                inProgress: %s
                todo:
                %s
                lastFailure: %s
                """.formatted(
                fallback(state.plan()),
                fallback(state.inProgress()),
                todoBlock,
                fallback(state.lastFailure())
        );
    }

    private static String fallback(String value) {
        return value == null || value.isBlank() ? "n/a" : value;
    }
}
