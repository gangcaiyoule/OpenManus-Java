package com.openmanus.agent.context.token;

import com.openmanus.agent.context.assembly.ContextBudgetPolicy;
import com.openmanus.agent.context.assembly.TaskExecutionState;
import com.openmanus.agent.context.compression.HistoricalContextSummarizer;
import com.openmanus.aiframework.runtime.model.AiChatMessage;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToIntFunction;

import static org.assertj.core.api.Assertions.assertThat;

class ContextSimplificationRegressionTest {

    @Test
    void tokenCounter_keepsApproxAndTokenizerFallbackModes() {
        ModelContextTokenCounter approx = ModelContextTokenCounter.create("approx", "glm-5.1");

        ModelContextTokenCounter tokenizer = ModelContextTokenCounter.create(
                "tokenizer",
                "glm-5.1",
                () -> {
                    throw new LinkageError("tokenizer unavailable");
                },
                ModelContextTokenCounter::lightweight
        );

        assertThat(approx).isSameAs(ModelContextTokenCounter.approx());
        assertThat(tokenizer).isSameAs(ModelContextTokenCounter.lightweight());
        assertThat(tokenizer.estimateTokens(AiChatMessage.user("hello"))).isGreaterThan(0);
    }

    @Test
    void modelTokenizer_inlinesModelEncodingResolution() {
        assertThat(ModelContextTokenCounter.forModel("glm-5.1").encodingTypeName())
                .isEqualTo("O200K_BASE");
        assertThat(ModelContextTokenCounter.forModel("text-davinci-003").encodingTypeName())
                .isEqualTo("R50K_BASE");
    }

    @Test
    void tokenBudget_keepsSystemCurrentUserAndLatestToolAnchors() {
        AiChatMessage system = AiChatMessage.system("system prompt");
        AiChatMessage oldUser = AiChatMessage.user("older request");
        AiChatMessage currentUser = AiChatMessage.user("current request");
        AiChatMessage assistant = AiChatMessage.assistant("working");
        AiChatMessage latestTool = new AiChatMessage(
                AiChatMessage.Role.TOOL,
                "tool observation",
                "fileTool",
                "call-1",
                List.of()
        );
        AiChatMessage tailAssistant = AiChatMessage.assistant("large tail");
        ToIntFunction<AiChatMessage> counter = message -> {
            if (message == system || message == currentUser || message == latestTool) {
                return 8;
            }
            return 999;
        };
        ContextBudgetPolicy policy = new ContextBudgetPolicy(0, 0, 24, counter);

        List<AiChatMessage> budgeted = policy.applyApproxTokenBudget(
                List.of(system, oldUser, currentUser, assistant, latestTool, tailAssistant),
                currentUser
        );

        assertThat(budgeted).containsExactly(system, currentUser, latestTool);
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

    @Test
    void historicalMemory_remainsIndependentOfTokenBudget() {
        AiChatMessage system = AiChatMessage.system("system");
        AiChatMessage droppedUser = AiChatMessage.user("summarize the build");
        AiChatMessage droppedAssistant = AiChatMessage.assistant("build summary done");
        AiChatMessage retainedToolStub = new AiChatMessage(
                AiChatMessage.Role.TOOL,
                "[Tool Result Stub]\npath=.openmanus/tool-results/20260503-010203004-abcdef.txt\n",
                "fileTool",
                "call-1",
                List.of()
        );
        List<AiChatMessage> enriched = new HistoricalContextSummarizer().inject(
                List.of(system, droppedUser, droppedAssistant, retainedToolStub),
                List.of(system)
        );

        assertThat(enriched).hasSize(2);
        assertThat(enriched.get(1).content()).contains("[Historical Key Memory]");
    }
}
