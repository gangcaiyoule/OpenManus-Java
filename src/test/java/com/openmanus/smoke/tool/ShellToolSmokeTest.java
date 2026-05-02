package com.openmanus.smoke.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.aiframework.runtime.AiSessionSandboxGateway;
import com.openmanus.aiframework.runtime.AiToolResultArtifactStore;
import com.openmanus.agent.tool.ShellTool;
import com.openmanus.smoke.SmokeTest;
import com.openmanus.sandbox.support.SandboxPathResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.AfterEach;
import org.slf4j.MDC;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.eq;

@Tag("smoke")
@DisplayName("ShellTool Smoke Tests")
class ShellToolSmokeTest implements SmokeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TEST_SESSION_ID = "test-shell-session";

    @TempDir
    Path tempDir;

    private AiSessionSandboxGateway mockGateway;
    private ShellTool shellTool;
    private InMemoryArtifactStore artifactStore;

    @BeforeEach
    void setUp() {
        MDC.put("sessionId", TEST_SESSION_ID);
        mockGateway = mock(AiSessionSandboxGateway.class);
        when(mockGateway.resolveWorkspacePath(anyString(), anyString())).thenAnswer(invocation -> {
            String userPath = invocation.getArgument(1, String.class);
            Path resolved = ".".equals(userPath) ? tempDir : tempDir.resolve(userPath).normalize();
            if (!resolved.startsWith(tempDir)) {
                throw new SecurityException("禁止访问沙盒外路径: " + userPath);
            }
            return resolved.toString();
        });
        SandboxPathResolver resolver = new SandboxPathResolver(mockGateway);
        artifactStore = new InMemoryArtifactStore();
        shellTool = new ShellTool(mockGateway, resolver, artifactStore, true, 1, 8000);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("should execute shell command and return fixed metadata")
    void runShellCommand_basic_returnsMetadata() throws Exception {
        when(mockGateway.executeCommand(eq(TEST_SESSION_ID), eq("echo hello"), eq(tempDir.toString()), eq(1)))
                .thenReturn(new com.openmanus.aiframework.runtime.AiSandboxCommandResult("hello\n", "", 0));
        String result = shellTool.runShellCommand("echo hello", null, "memory-1");
        JsonNode node = MAPPER.readTree(result);
        assertThat(node.get("command").asText()).isEqualTo("echo hello");
        assertThat(node.get("exitCode").asInt()).isEqualTo(0);
        assertThat(node.get("cwd").asText()).contains(tempDir.toString());
        assertThat(node.get("stdout").asText()).contains("hello");
        assertThat(node.get("stderr").asText()).isEmpty();
        assertThat(node.get("truncated").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("should block sandbox escape via cwd")
    void runShellCommand_withSandboxEscapeCwd_returnsError() throws Exception {
        String result = shellTool.runShellCommand("echo hello", "../../../../..", "memory-2");
        JsonNode node = MAPPER.readTree(result);
        assertThat(node.get("exitCode").asInt()).isEqualTo(-1);
        assertThat(node.get("stderr").asText()).contains("禁止访问沙盒外路径");
    }

    @Test
    @DisplayName("should offload long output and return artifactId + preview")
    void runShellCommand_longOutput_offloads() throws Exception {
        String cmd = "dd if=/dev/zero bs=1 count=9000 2>/dev/null | tr '\\\\0' 'a'";
        when(mockGateway.executeCommand(eq(TEST_SESSION_ID), eq(cmd), eq(tempDir.toString()), eq(1)))
                .thenReturn(new com.openmanus.aiframework.runtime.AiSandboxCommandResult("a".repeat(9000), "", 0));
        String result = shellTool.runShellCommand(cmd, null, "memory-3");
        JsonNode node = MAPPER.readTree(result);
        assertThat(node.get("exitCode").asInt()).isEqualTo(0);
        assertThat(node.get("truncated").asBoolean()).isTrue();
        assertThat(node.get("artifactId").asText()).isNotBlank();
        assertThat(node.get("preview").asText()).contains("[Shell Output]");

        Optional<String> full = artifactStore.load(node.get("artifactId").asText());
        assertThat(full).isPresent();
        assertThat(full.get()).contains("--- stdout ---");
        assertThat(full.get().length()).isGreaterThan(8000);
    }

    private static final class InMemoryArtifactStore implements AiToolResultArtifactStore {

        private final AtomicInteger seq = new AtomicInteger(0);
        private final Map<String, String> store = new ConcurrentHashMap<>();

        @Override
        public String save(Object memoryId, String toolName, String toolArguments, String outcome) {
            String id = toolName + "-" + seq.incrementAndGet();
            store.put(id, outcome);
            return id;
        }

        @Override
        public Optional<String> load(String artifactId) {
            return Optional.ofNullable(store.get(artifactId));
        }
    }
}
