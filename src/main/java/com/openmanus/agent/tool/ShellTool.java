package com.openmanus.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openmanus.aiframework.runtime.AiSandboxCommandResult;
import com.openmanus.aiframework.runtime.AiSessionSandboxGateway;
import com.openmanus.aiframework.tool.AiParam;
import com.openmanus.aiframework.tool.AiTool;
import com.openmanus.sandbox.support.SandboxPathResolver;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Objects;

@Slf4j
public class ShellTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AiSessionSandboxGateway sessionSandboxGateway;
    private final SandboxPathResolver sandboxPathResolver;
    private final boolean enabled;
    private final int timeoutSeconds;

    public ShellTool(AiSessionSandboxGateway sessionSandboxGateway,
                     SandboxPathResolver sandboxPathResolver,
                     boolean enabled,
                     int timeoutSeconds) {
        this.sessionSandboxGateway = Objects.requireNonNull(sessionSandboxGateway, "sessionSandboxGateway");
        this.sandboxPathResolver = Objects.requireNonNull(sandboxPathResolver, "sandboxPathResolver");
        this.enabled = enabled;
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
    }

    @AiTool(value = "通用 Shell 命令执行（用于文件发现/定位/分段读取）", name = "runShellCommand")
    public String runShellCommand(@AiParam("Shell 命令") String command,
                                  @AiParam(value = "工作目录（可选，默认沙盒根目录）", required = false) String cwd,
                                  Object memoryId) {
        if (!enabled) {
            return jsonError(command, cwd, "shell tool disabled");
        }
        if (command == null || command.isBlank()) {
            return jsonError(command, cwd, "command is blank");
        }

        String sandboxKey = sandboxPathResolver.currentSandboxKey();

        java.nio.file.Path workingDir;
        try {
            workingDir = sandboxPathResolver.resolveSandboxPath(cwd == null || cwd.isBlank() ? "." : cwd);
        } catch (SecurityException e) {
            return jsonError(command, cwd, e.getMessage());
        }

        Instant startedAt = Instant.now();
        AiSandboxCommandResult result = sessionSandboxGateway.executeCommand(
                sandboxKey,
                command,
                workingDir.toString(),
                timeoutSeconds
        );
        String stdout = safeText(result.stdout());
        String stderr = safeText(result.stderr());
        int exitCode = result.exitCode();
        boolean timedOut = exitCode == 124;

        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("command", command);
        root.put("exitCode", exitCode);
        root.put("cwd", workingDir.toString());
        root.put("stdout", stdout);
        root.put("stderr", stderr);
        root.put("truncated", false);
        root.put("timeoutSeconds", timeoutSeconds);
        root.put("startedAtEpochMs", startedAt.toEpochMilli());
        root.put("finishedAtEpochMs", Instant.now().toEpochMilli());
        if (timedOut) {
            root.put("timedOut", true);
        }
        return root.toString();
    }

    private static String safeText(String text) {
        return text == null ? "" : text;
    }

    private static String jsonError(String command, String cwd, String message) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("command", command == null ? "" : command);
        root.put("exitCode", -1);
        root.put("cwd", cwd == null ? "" : cwd);
        root.put("stdout", "");
        root.put("stderr", message == null ? "" : message);
        root.put("truncated", false);
        return root.toString();
    }
}
