package com.openmanus.smoke.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.aiframework.runtime.AiChatModel;
import com.openmanus.aiframework.runtime.AiSandboxCommandResult;
import com.openmanus.aiframework.runtime.AiSessionSandboxGateway;
import com.openmanus.aiframework.runtime.AiSessionSandboxInfo;
import com.openmanus.aiframework.runtime.model.AiAgentParameterSchema;
import com.openmanus.aiframework.runtime.model.AiChatMessage;
import com.openmanus.aiframework.runtime.model.AiChatRequest;
import com.openmanus.aiframework.runtime.model.AiChatResponse;
import com.openmanus.aiframework.runtime.model.AiFinishReason;
import com.openmanus.aiframework.runtime.model.AiTokenUsage;
import com.openmanus.aiframework.runtime.model.AiToolCall;
import com.openmanus.aiframework.tool.AiRegisteredTool;
import com.openmanus.agent.coordination.AgentCoordinator;
import com.openmanus.agent.tool.ShellTool;
import com.openmanus.sandbox.support.SandboxPathResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("smoke")
@DisplayName("Agent tool result budget E2E")
class AgentToolResultBudgetE2ETest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern STUB_PATH = Pattern.compile("path=(\\.openmanus/tool-results/[^\\s]+)");
    private static final String SESSION_ID = "budget-e2e-session";
    private static final String LARGE_OUTPUT = "large-result-line\n" + "x".repeat(1800);

    @TempDir
    Path workspace;

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("large tool result is stubbed before next model request and can be read explicitly")
    void largeToolResult_isStubbedAndReadableExplicitly() {
        MDC.put("sessionId", SESSION_ID);
        MDC.put("userId", "001");
        WorkspaceGateway gateway = new WorkspaceGateway(workspace);
        BudgetAwareModel model = new BudgetAwareModel();
        SandboxPathResolver pathResolver = new SandboxPathResolver(gateway);
        ShellTool shellTool = new ShellTool(gateway, pathResolver, true, 3);
        AgentCoordinator agent = AgentCoordinator.builder()
                .aiChatModel(model)
                .sessionSandboxGateway(gateway)
                .maxIterations(5)
                .toolResultBudgetMinChars(1200)
                .toolResultBudgetPreviewHeadChars(64)
                .toolResultBudgetPreviewTailChars(32)
                .tool(new AiRegisteredTool(
                        "produceLargeResult",
                        "Returns a large deterministic result",
                        AiAgentParameterSchema.singleStringParameter("input", "input"),
                        (request, memoryId) -> LARGE_OUTPUT
                ))
                .shellTool(shellTool)
                .build();

        String result = agent.execute("trigger large result", SESSION_ID);

        assertThat(result).isEqualTo("done");
        assertThat(model.requests).hasSize(3);
        assertThat(model.requests)
                .allSatisfy(request -> assertThat(request.toolSpecs())
                        .extracting("name")
                        .doesNotContain("File" + "Read"));
        AiChatRequest secondRequest = model.requests.get(1);
        String toolContent = secondRequest.messages().stream()
                .filter(message -> message.role() == AiChatMessage.Role.TOOL)
                .findFirst()
                .map(AiChatMessage::content)
                .orElse("");
        assertThat(toolContent).startsWith("[Tool Result Stub]");
        assertThat(toolContent).contains("path=.openmanus/tool-results/");
        assertThat(toolContent).contains("readHint=Use runShellCommand with cat/head/tail/grep/rg");
        assertThat(toolContent).doesNotContain("File" + "Read");
        assertThat(toolContent).doesNotContain("x".repeat(300));

        Path offloadedPath = workspace.resolve(model.stubPath).normalize();
        assertThat(offloadedPath).exists();
        assertThat(Files.exists(workspace.resolve(".openmanus").resolve("web"))).isFalse();
        assertThat(readUnchecked(offloadedPath)).isEqualTo(LARGE_OUTPUT);

        String finalShellContent = model.requests.get(2).messages().stream()
                .filter(message -> message.role() == AiChatMessage.Role.TOOL)
                .filter(message -> "runShellCommand".equals(message.name()))
                .findFirst()
                .map(AiChatMessage::content)
                .orElse("");
        assertThat(finalShellContent).contains("large-result-line");
        assertThat(finalShellContent).doesNotStartWith("[Tool Result Stub]");
    }

    private static String readUnchecked(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static AiChatResponse response(AiChatMessage message) {
        return new AiChatResponse(
                message,
                AiFinishReason.STOP,
                new AiTokenUsage(10, 5, 15),
                "resp",
                "mock",
                null
        );
    }

    private static final class BudgetAwareModel implements AiChatModel {
        private final List<AiChatRequest> requests = new ArrayList<>();
        private String stubPath;

        @Override
        public AiChatResponse chat(AiChatRequest request) {
            requests.add(request);
            if (requests.size() == 1) {
                return response(AiChatMessage.assistant("produce", List.of(
                        new AiToolCall("call-large", "produceLargeResult", "{\"input\":\"go\"}")
                )));
            }
            if (requests.size() == 2) {
                String toolContent = request.messages().stream()
                        .filter(message -> message.role() == AiChatMessage.Role.TOOL)
                        .findFirst()
                        .map(AiChatMessage::content)
                        .orElseThrow();
                Matcher matcher = STUB_PATH.matcher(toolContent);
                assertThat(matcher.find()).isTrue();
                stubPath = matcher.group(1);
                return response(AiChatMessage.assistant("shell-read", List.of(
                        new AiToolCall("call-shell", "runShellCommand",
                                "{\"command\":\"head -n 1 " + stubPath + "\",\"cwd\":\".\"}")
                )));
            }
            return response(AiChatMessage.assistant("done"));
        }
    }

    private static final class WorkspaceGateway implements AiSessionSandboxGateway {
        private final Path workspace;

        private WorkspaceGateway(Path workspace) {
            this.workspace = workspace.normalize();
        }

        @Override
        public Optional<AiSessionSandboxInfo> getSandboxInfo(String sessionId) {
            return Optional.empty();
        }

        @Override
        public AiSessionSandboxInfo getOrCreateSandbox(String sessionId) {
            return new AiSessionSandboxInfo(sessionId, null, workspace.toString(), "", null, "RUNNING");
        }

        @Override
        public String getWorkspaceRoot(String sessionId) {
            return workspace.toString();
        }

        @Override
        public String resolveWorkspacePath(String sessionId, String userPath) {
            Path resolved = ".".equals(userPath)
                    ? workspace
                    : workspace.resolve(userPath).normalize();
            if (!resolved.startsWith(workspace)) {
                throw new SecurityException("禁止访问沙盒外路径: " + userPath);
            }
            return resolved.toString();
        }

        @Override
        public AiSandboxCommandResult executeCommand(String sessionId, String command, String cwd, int timeoutSeconds) {
            if (!command.startsWith("head -n 1 ")) {
                return new AiSandboxCommandResult("", "unsupported command", 2);
            }
            String path = command.substring("head -n 1 ".length()).trim();
            String content = readTextFile(sessionId, resolveWorkspacePath(sessionId, path));
            int newline = content.indexOf('\n');
            return new AiSandboxCommandResult(newline >= 0 ? content.substring(0, newline + 1) : content, "", 0);
        }

        @Override
        public AiSandboxCommandResult openBrowserUrl(String sessionId, String url) {
            return new AiSandboxCommandResult("", "", 0);
        }

        @Override
        public String readTextFile(String sessionId, String path) {
            try {
                Path resolved = Path.of(path).normalize();
                if (!resolved.startsWith(workspace)) {
                    throw new SecurityException("禁止访问沙盒外路径: " + path);
                }
                return Files.readString(resolved, StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void writeTextFile(String sessionId, String path, String content) {
            try {
                Path resolved = Path.of(path).normalize();
                if (!resolved.startsWith(workspace)) {
                    throw new SecurityException("禁止访问沙盒外路径: " + path);
                }
                Files.createDirectories(resolved.getParent());
                Files.writeString(resolved, content, StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
