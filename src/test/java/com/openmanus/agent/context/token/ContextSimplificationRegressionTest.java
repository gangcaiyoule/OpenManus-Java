package com.openmanus.agent.context.token;

import com.openmanus.agent.context.assembly.ContextBudgetPolicy;
import com.openmanus.agent.context.assembly.TaskExecutionState;
import com.openmanus.agent.context.compression.HistoricalContextSummarizer;
import com.openmanus.agent.context.compression.IndexedRehydrateSelector;
import com.openmanus.agent.context.compression.ToolResultContextCompressor;
import com.openmanus.aiframework.runtime.ToolResultArtifactRef;
import com.openmanus.aiframework.runtime.model.AiChatMessage;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToIntFunction;

import static org.assertj.core.api.Assertions.assertThat;

class ContextSimplificationRegressionTest {

    private static final String ARTIFACT_ID =
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

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
    void compressionRehydrateAndHistoricalMemory_remainIndependent() {
        AiChatMessage toolResult = new AiChatMessage(
                AiChatMessage.Role.TOOL,
                ("artifactId=" + ARTIFACT_ID + "\n").repeat(20),
                "fileTool",
                "call-1",
                List.of()
        );
        ToolResultContextCompressor compressor = new ToolResultContextCompressor(256, 64, 32);

        AiChatMessage compressed = compressor.compress(List.of(toolResult)).getFirst();

        assertThat(compressed.content()).startsWith("[Tool Result Context Compressed]");
        assertThat(compressed.content()).contains("artifactId=" + ARTIFACT_ID);

        ToolResultArtifactRef ref = new ToolResultArtifactRef(ARTIFACT_ID, "fileTool", "{}", 5000, 10L);
        List<ToolResultArtifactRef> selected = IndexedRehydrateSelector.select(
                List.of(ref),
                List.of(compressed),
                AiChatMessage.user("show " + ARTIFACT_ID),
                1
        );

        assertThat(selected).containsExactly(ref);

        AiChatMessage system = AiChatMessage.system("system");
        AiChatMessage droppedUser = AiChatMessage.user("summarize the build");
        AiChatMessage droppedAssistant = AiChatMessage.assistant("build summary done");
        List<AiChatMessage> enriched = new HistoricalContextSummarizer().inject(
                List.of(system, droppedUser, droppedAssistant, compressed),
                List.of(system)
        );

        assertThat(enriched).hasSize(2);
        assertThat(enriched.get(1).content()).contains("[Historical Key Memory]");
    }
}
