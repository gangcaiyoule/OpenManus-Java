package com.openmanus.agent.context;

import com.openmanus.aiframework.runtime.AiSessionSandboxGateway;
import com.openmanus.aiframework.runtime.model.AiChatMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolResultBudgetTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void budgetsLargeToolResultIntoSandboxStub() {
        AiSessionSandboxGateway gateway = mock(AiSessionSandboxGateway.class);
        when(gateway.resolveWorkspacePath(eq("001"), contains(".openmanus/tool-results/")))
                .thenAnswer(invocation -> "/workspace/" + invocation.getArgument(1, String.class));
        ToolResultBudget budget = new ToolResultBudget(gateway, true, 256, 20, 10, 0);
        AiChatMessage raw = new AiChatMessage(
                AiChatMessage.Role.TOOL,
                "a".repeat(300),
                "runShellCommand",
                "call-1",
                List.of()
        );

        AiChatMessage result = budget.budget(raw);

        assertThat(result.content()).startsWith("[Tool Result Stub]");
        assertThat(result.content()).contains("path=.openmanus/tool-results/");
        assertThat(result.content()).contains("originalChars=300");
        assertThat(result.content()).contains("sha256=");
        assertThat(result.content()).contains("truncated=true");
        assertThat(result.content()).contains("readHint=Use runShellCommand with cat/head/tail/grep/rg");
        assertThat(result.content()).doesNotContain("File" + "Read");
        assertThat(result.content()).contains("previewHead:");
        assertThat(result.content()).contains("previewTail:");
        verify(gateway).writeTextFile(eq("001"), contains(".openmanus/tool-results/"), eq(raw.content()));
    }

    @Test
    void keepsSmallToolResultInline() {
        AiSessionSandboxGateway gateway = mock(AiSessionSandboxGateway.class);
        ToolResultBudget budget = new ToolResultBudget(gateway, true, 256, 20, 10, 0);
        AiChatMessage raw = new AiChatMessage(AiChatMessage.Role.TOOL, "small", "tool", "call-1", List.of());

        assertThat(budget.budget(raw)).isSameAs(raw);
    }
}
