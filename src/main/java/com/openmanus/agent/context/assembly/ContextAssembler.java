package com.openmanus.agent.context.assembly;

import com.openmanus.aiframework.runtime.model.AiChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Assembles model input in one place:
 * full history -> current user turn -> task-state card.
 */
public final class ContextAssembler {

    private final TaskStateContextInjector taskStateContextInjector;

    public ContextAssembler(TaskExecutionState.Budget taskStateBudget) {
        this(new TaskStateContextInjector(taskStateBudget));
    }

    ContextAssembler(TaskStateContextInjector taskStateContextInjector) {
        this.taskStateContextInjector = taskStateContextInjector == null
                ? new TaskStateContextInjector()
                : taskStateContextInjector;
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
                    : new ArrayList<>(currentTurnMessages);
            return taskStateContextInjector.inject(seedMessages, taskExecutionState);
        }

        List<AiChatMessage> result = new ArrayList<>();
        if (currentTurnMessages.isEmpty()) {
            result.addAll(snapshot.historicalMessages());
            if (currentUserMessage != null) {
                result.add(currentUserMessage);
            }
            return taskStateContextInjector.inject(result, taskExecutionState);
        }

        result.addAll(snapshot.historicalMessages());
        result.addAll(currentTurnMessages);
        return taskStateContextInjector.inject(result, taskExecutionState);
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
