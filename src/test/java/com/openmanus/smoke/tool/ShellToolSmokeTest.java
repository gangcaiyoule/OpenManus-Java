package com.openmanus.smoke.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.aiframework.runtime.AiSandboxCommandResult;
import com.openmanus.aiframework.runtime.AiSessionSandboxGateway;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.eq;

@Tag("smoke")
@DisplayName("ShellTool Smoke Tests")
class ShellToolSmokeTest implements SmokeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TEST_USER_ID = "001";

    @TempDir
    Path tempDir;

    private AiSessionSandboxGateway mockGateway;
    private ShellTool shellTool;

    @BeforeEach
    void setUp() {
        MDC.put("sessionId", "test-shell-session");
        MDC.put("userId", TEST_USER_ID);
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
        shellTool = new ShellTool(mockGateway, resolver, true, 1);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("should execute shell command and return fixed metadata")
    void runShellCommand_basic_returnsMetadata() throws Exception {
        when(mockGateway.executeCommand(eq(TEST_USER_ID), eq("echo hello"), eq(tempDir.toString()), eq(1)))
                .thenReturn(new AiSandboxCommandResult("hello\n", "", 0));
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
    @DisplayName("should return raw long output and leave budgeting to executor")
    void runShellCommand_longOutput_returnsRaw() throws Exception {
        String cmd = "printf long-output";
        when(mockGateway.executeCommand(eq(TEST_USER_ID), eq(cmd), eq(tempDir.toString()), eq(1)))
                .thenReturn(new AiSandboxCommandResult("a".repeat(9000), "", 0));
        String result = shellTool.runShellCommand(cmd, null, "memory-3");
        JsonNode node = MAPPER.readTree(result);
        assertThat(node.get("exitCode").asInt()).isEqualTo(0);
        assertThat(node.get("truncated").asBoolean()).isFalse();
        assertThat(node.has("artifact" + "Id")).isFalse();
        assertThat(node.get("stdout").asText()).hasSize(9000);
    }

    @Test
    @DisplayName("should keep shell syntax executable")
    void runShellCommand_shellSyntax_executes() throws Exception {
        assertAllowed("echo hello > out.txt");
        assertAllowed("printf ok && echo done");
        assertAllowed("echo $(pwd)");
    }

    @Test
    @DisplayName("should execute common read commands without command-level rejection")
    void runShellCommand_readCommands_executeInSandbox() throws Exception {
        assertAllowed("cat file.txt");
        assertAllowed("head -n 5 file.txt");
        assertAllowed("tail -n 5 file.txt");
        assertAllowed("grep pattern file.txt");
        assertAllowed("rg pattern dir");
    }

    @Test
    @DisplayName("should execute complex read commands in docker sandbox")
    void runShellCommand_complexReadCommands_executeInSandbox() throws Exception {
        assertAllowed("cat /etc/passwd");
        assertAllowed("grep x ../outside.txt");
        assertAllowed("cat file.txt; cat /etc/passwd");
        assertAllowed("cat $(pwd)/file.txt");
        assertAllowed("echo $(cat /etc/passwd)");
        assertAllowed("cat `pwd`/file.txt");
        assertAllowed("cat file.txt > out.txt");
        assertAllowed("printf /etc/passwd | xargs cat");
        assertAllowed("sh -c 'cat /etc/passwd'");
    }

    private void assertAllowed(String command) throws Exception {
        when(mockGateway.executeCommand(eq(TEST_USER_ID), eq(command), eq(tempDir.toString()), eq(1)))
                .thenReturn(new AiSandboxCommandResult("ok\n", "", 0));
        String result = shellTool.runShellCommand(command, null, "memory-read");
        JsonNode node = MAPPER.readTree(result);
        assertThat(node.get("exitCode").asInt()).isEqualTo(0);
        assertThat(node.get("stdout").asText()).contains("ok");
    }

}
