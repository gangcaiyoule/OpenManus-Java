package com.openmanus.infra.sandbox;

import com.openmanus.infra.config.OpenManusProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("smoke")
@DisplayName("SandboxClient Docker integration tests")
class SandboxClientDockerIntegrationTest {

    private final List<String> createdSessionIds = new ArrayList<>();
    private SandboxClient sandboxClient;

    @AfterEach
    void tearDown() throws Exception {
        if (sandboxClient != null) {
            for (String sessionId : createdSessionIds) {
                sandboxClient.destroySessionContainer(sessionId);
            }
            sandboxClient.close();
        }
    }

    @Test
    @DisplayName("writeTextFile should copy archive into the expected absolute workspace path")
    void writeTextFile_copiesArchiveIntoExpectedAbsolutePath() {
        sandboxClient = createRealSandboxClientOrSkip();
        String sessionId = newSessionId();
        String path = "/workspace/.openmanus/tool-results/integration-check.txt";
        String content = "tool-result-line\n" + "x".repeat(220000);

        sandboxClient.writeTextFile(sessionId, path, content);

        String readBack = sandboxClient.readTextFile(sessionId, path);
        assertThat(readBack).isEqualTo(content);

        ExecutionResult lsResult = sandboxClient.executeCommand(
                sessionId,
                "test -f /workspace/.openmanus/tool-results/integration-check.txt && echo exists",
                "/workspace",
                10
        );
        assertThat(lsResult.isSuccess()).isTrue();
        assertThat(lsResult.getStdout()).contains("exists");
    }

    @Test
    @DisplayName("executePython should run a large script through stdin in a real container")
    void executePython_runsLargeScriptThroughStdinInRealContainer() {
        sandboxClient = createRealSandboxClientOrSkip();
        String sessionId = newSessionId();
        String script = "print('line')\n".repeat(50000) + "print('done')";

        ExecutionResult result = sandboxClient.executePython(sessionId, script, 20);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStdout()).contains("done");
        assertThat(result.getStderr()).isEmpty();
    }

    private SandboxClient createRealSandboxClientOrSkip() {
        try {
            return new SandboxClient(new OpenManusProperties());
        } catch (RuntimeException e) {
            Assumptions.abort("Docker sandbox unavailable for integration test: " + e.getMessage());
            throw e;
        }
    }

    private String newSessionId() {
        String sessionId = "docker-it-" + UUID.randomUUID();
        createdSessionIds.add(sessionId);
        return sessionId;
    }
}
