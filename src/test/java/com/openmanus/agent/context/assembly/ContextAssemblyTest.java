package com.openmanus.agent.context.assembly;

import com.openmanus.aiframework.runtime.model.AiChatMessage;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContextAssemblyTest {

    @Test
    void assemble_keepsFullHistoryWithoutTrimming() {
        ContextAssembler assembler = new ContextAssembler(TaskExecutionState.Budget.defaults());
        AiChatMessage system = AiChatMessage.system("system");
        AiChatMessage oldUser = AiChatMessage.user("older request");
        AiChatMessage oldAssistant = AiChatMessage.assistant("older answer");
        AiChatMessage currentUser = AiChatMessage.user("current request");
        AiChatMessage currentAssistant = AiChatMessage.assistant("current answer");

        List<AiChatMessage> assembled = assembler.assemble(
                ContextSnapshot.from(
                        List.of(system, oldUser, oldAssistant, currentUser, currentAssistant),
                        currentUser
                ),
                TaskExecutionState.empty()
        );

        assertThat(assembled).containsExactly(system, oldUser, oldAssistant, currentUser, currentAssistant);
    }

    @Test
    void assemble_appendsTaskStateCardWithoutReorderingMessages() {
        ContextAssembler assembler = new ContextAssembler(TaskExecutionState.Budget.defaults());
        AiChatMessage currentUser = AiChatMessage.user("current request");
        TaskExecutionState state = TaskExecutionState.from(
                "plan",
                "running",
                List.of("step-a", "step-b"),
                "none",
                TaskExecutionState.Budget.defaults()
        );

        List<AiChatMessage> assembled = assembler.assemble(
                ContextSnapshot.from(List.of(currentUser), currentUser),
                state
        );

        assertThat(assembled).hasSize(2);
        assertThat(assembled.get(0)).isEqualTo(currentUser);
        assertThat(assembled.get(1).role()).isEqualTo(AiChatMessage.Role.ASSISTANT);
        assertThat(assembled.get(1).content()).contains("[Task State]");
    }

    @Test
    void taskStateBudget_trimsFieldsAndTodo() {
        TaskExecutionState.Budget budget = new TaskExecutionState.Budget(5, 4, 6, 1, 3);

        TaskExecutionState state = TaskExecutionState.from(
                "abcdef",
                "wxyzv",
                List.of("one", "two"),
                "failure long",
                budget
        );

        assertThat(state.plan()).isEqualTo("abcde");
        assertThat(state.inProgress()).isEqualTo("wxyz");
        assertThat(state.todo()).containsExactly("one");
        assertThat(state.lastFailure()).isEqualTo("failur");
    }
}
