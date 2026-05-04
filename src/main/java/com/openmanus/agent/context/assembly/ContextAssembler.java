package com.openmanus.agent.context.assembly;

import com.openmanus.agent.context.compression.HistoricalContextSummarizer;
import com.openmanus.aiframework.runtime.model.AiChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Assembles model input in one place:
 * system/history -> current user turn -> latest tool observations.
 */
public final class ContextAssembler {

    private final ContextBudgetPolicy budgetPolicy;
    private final TaskStateContextInjector taskStateContextInjector;
    private final HistoricalContextSummarizer historicalContextSummarizer;

    public ContextAssembler(ContextBudgetPolicy budgetPolicy, TaskExecutionState.Budget taskStateBudget) {
        this(
                budgetPolicy,
                new TaskStateContextInjector(taskStateBudget),
                new HistoricalContextSummarizer()
        );
    }

    ContextAssembler(ContextBudgetPolicy budgetPolicy,
                     TaskStateContextInjector taskStateContextInjector,
                     HistoricalContextSummarizer historicalContextSummarizer) {
        this.budgetPolicy = budgetPolicy == null
                ? ContextBudgetPolicy.defaults()
                : budgetPolicy;
        this.taskStateContextInjector = taskStateContextInjector == null
                ? new TaskStateContextInjector()
                : taskStateContextInjector;
        this.historicalContextSummarizer = historicalContextSummarizer == null
                ? new HistoricalContextSummarizer()
                : historicalContextSummarizer;
    }

    public List<AiChatMessage> assemble(ContextSnapshot snapshot) {
        return assemble(snapshot, TaskExecutionState.empty());
    }

    public List<AiChatMessage> assemble(ContextSnapshot snapshot, TaskExecutionState taskExecutionState) {
        if (snapshot == null) {
            return List.of();
        }
        List<AiChatMessage> fullMessages = snapshot.fullMessages();
        AiChatMessage currentUserMessage = snapshot.currentUserMessage();
        List<AiChatMessage> currentTurnMessages = ensureCurrentUserAnchor(
                snapshot.currentTurnMessages(),
                currentUserMessage
        );

        if (fullMessages == null || fullMessages.isEmpty()) {
            List<AiChatMessage> seedMessages = currentTurnMessages.isEmpty()
                    ? (currentUserMessage == null ? List.of() : List.of(currentUserMessage))
                    : budgetPolicy.trimForTotalLimit(currentTurnMessages, currentUserMessage);
            return taskStateContextInjector.inject(seedMessages, taskExecutionState, currentUserMessage, budgetPolicy);
        }

        List<AiChatMessage> result = new ArrayList<>();
        if (currentTurnMessages.isEmpty()) {
            List<AiChatMessage> trimmedHistory = budgetPolicy.trimHistory(snapshot.historicalMessages());
            result.addAll(historicalContextSummarizer.inject(snapshot.historicalMessages(), trimmedHistory));
            if (currentUserMessage != null) {
                result.add(currentUserMessage);
            }
            return taskStateContextInjector.inject(
                    budgetPolicy.trimForTotalLimit(result, currentUserMessage),
                    taskExecutionState,
                    currentUserMessage,
                    budgetPolicy
            );
        }

        List<AiChatMessage> trimmedHistory = budgetPolicy.trimHistory(snapshot.historicalMessages());
        result.addAll(historicalContextSummarizer.inject(snapshot.historicalMessages(), trimmedHistory));
        result.addAll(currentTurnMessages);
        return taskStateContextInjector.inject(
                budgetPolicy.trimForTotalLimit(result, currentUserMessage),
                taskExecutionState,
                currentUserMessage,
                budgetPolicy
        );
    }

    private List<AiChatMessage> ensureCurrentUserAnchor(List<AiChatMessage> currentTurnMessages,
                                                        AiChatMessage currentUserMessage) {
        if (currentTurnMessages == null || currentTurnMessages.isEmpty()) {
            return List.of();
        }
        if (currentUserMessage == null || currentTurnMessages.contains(currentUserMessage)) {
            return currentTurnMessages;
        }
        List<AiChatMessage> anchored = new ArrayList<>(currentTurnMessages.size() + 1);
        anchored.add(currentUserMessage);
        anchored.addAll(currentTurnMessages);
        return anchored;
    }
}
