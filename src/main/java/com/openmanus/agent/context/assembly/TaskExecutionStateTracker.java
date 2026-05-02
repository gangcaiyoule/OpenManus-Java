package com.openmanus.agent.context.assembly;

import com.openmanus.aiframework.runtime.model.AiChatMessage;
import com.openmanus.aiframework.runtime.model.AiToolCall;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tracks task-state transitions during one CodeAct loop.
 */
public final class TaskExecutionStateTracker {

    private static final TaskExecutionState.Budget DEFAULT_BUDGET = TaskExecutionState.Budget.defaults();

    private TaskExecutionStateTracker() {
    }

    public static TaskExecutionState updateFromAssistantPlan(TaskExecutionState current,
                                                             AiChatMessage assistantMessage) {
        return updateFromAssistantPlan(current, assistantMessage, DEFAULT_BUDGET);
    }

    public static TaskExecutionState updateFromAssistantPlan(TaskExecutionState current,
                                                             AiChatMessage assistantMessage,
                                                             TaskExecutionState.Budget budget) {
        TaskExecutionState.Budget effectiveBudget = budget == null ? DEFAULT_BUDGET : budget;
        TaskExecutionState base = current == null ? TaskExecutionState.empty() : current;
        if (assistantMessage == null || assistantMessage.role() != AiChatMessage.Role.ASSISTANT) {
            return base;
        }
        String nextPlan = firstNonEmptyLine(assistantMessage.content());
        List<String> todo = extractToolNames(assistantMessage.toolCalls());
        if (todo.isEmpty()) {
            return nextPlan == null ? base : base.withPlan(nextPlan, effectiveBudget);
        }
        return TaskExecutionState.from(
                nextPlan == null ? base.plan() : nextPlan,
                todo.getFirst(),
                todo,
                null,
                effectiveBudget
        );
    }

    public static TaskExecutionState markToolStarted(TaskExecutionState current, String toolName) {
        return markToolStarted(current, toolName, DEFAULT_BUDGET);
    }

    public static TaskExecutionState markToolStarted(TaskExecutionState current,
                                                     String toolName,
                                                     TaskExecutionState.Budget budget) {
        TaskExecutionState.Budget effectiveBudget = budget == null ? DEFAULT_BUDGET : budget;
        TaskExecutionState base = current == null ? TaskExecutionState.empty() : current;
        if (toolName == null || toolName.isBlank()) {
            return base;
        }
        return base.withInProgress(toolName, effectiveBudget);
    }

    public static TaskExecutionState markToolSucceeded(TaskExecutionState current, String toolName) {
        return markToolSucceeded(current, toolName, DEFAULT_BUDGET);
    }

    public static TaskExecutionState markToolSucceeded(TaskExecutionState current,
                                                       String toolName,
                                                       TaskExecutionState.Budget budget) {
        TaskExecutionState.Budget effectiveBudget = budget == null ? DEFAULT_BUDGET : budget;
        TaskExecutionState base = current == null ? TaskExecutionState.empty() : current;
        if (toolName == null || toolName.isBlank()) {
            return base;
        }
        List<String> remaining = new ArrayList<>();
        boolean removed = false;
        for (String item : base.todo()) {
            if (!removed && toolName.equals(item)) {
                removed = true;
                continue;
            }
            remaining.add(item);
        }
        String nextInProgress = remaining.isEmpty() ? null : remaining.getFirst();
        return TaskExecutionState.from(base.plan(), nextInProgress, remaining, null, effectiveBudget);
    }

    public static TaskExecutionState markToolFailed(TaskExecutionState current,
                                                    String toolName,
                                                    String errorMessage) {
        return markToolFailed(current, toolName, errorMessage, DEFAULT_BUDGET);
    }

    public static TaskExecutionState markToolFailed(TaskExecutionState current,
                                                    String toolName,
                                                    String errorMessage,
                                                    TaskExecutionState.Budget budget) {
        TaskExecutionState.Budget effectiveBudget = budget == null ? DEFAULT_BUDGET : budget;
        TaskExecutionState base = current == null ? TaskExecutionState.empty() : current;
        String failureTool = (toolName == null || toolName.isBlank()) ? "unknown_tool" : toolName;
        String detail = errorMessage == null || errorMessage.isBlank() ? "unknown error" : errorMessage.trim();
        String prefix = "tool=" + failureTool + "; reason=";
        int maxDetailChars = effectiveBudget.lastFailureMaxChars() - prefix.length();
        if (maxDetailChars <= 0) {
            detail = "";
        } else if (detail.length() > maxDetailChars) {
            if (maxDetailChars <= 3) {
                detail = detail.substring(0, maxDetailChars);
            } else {
                detail = detail.substring(0, maxDetailChars - 3) + "...";
            }
        }
        String failure = prefix + detail;
        return TaskExecutionState.from(base.plan(), failureTool, base.todo(), failure, effectiveBudget);
    }

    private static String firstNonEmptyLine(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String[] lines = content.split("\\R");
        for (String line : lines) {
            if (line != null && !line.isBlank()) {
                return line.trim();
            }
        }
        return null;
    }

    private static List<String> extractToolNames(List<AiToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (AiToolCall call : toolCalls) {
            if (call == null || call.name() == null || call.name().isBlank()) {
                continue;
            }
            unique.add(call.name().trim());
        }
        return List.copyOf(unique);
    }
}
