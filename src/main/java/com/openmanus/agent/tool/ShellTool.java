package com.openmanus.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openmanus.aiframework.runtime.AiSandboxCommandResult;
import com.openmanus.aiframework.runtime.AiSessionSandboxGateway;
import com.openmanus.aiframework.runtime.AiToolResultArtifactStore;
import com.openmanus.aiframework.tool.AiParam;
import com.openmanus.aiframework.tool.AiTool;
import com.openmanus.sandbox.support.SandboxPathResolver;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

@Slf4j
public class ShellTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AiSessionSandboxGateway sessionSandboxGateway;
    private final SandboxPathResolver sandboxPathResolver;
    private final AiToolResultArtifactStore toolResultArtifactStore;
    private final boolean enabled;
    private final int timeoutSeconds;
    private final int maxOutputChars;

    public ShellTool(AiSessionSandboxGateway sessionSandboxGateway,
                     SandboxPathResolver sandboxPathResolver,
                     AiToolResultArtifactStore toolResultArtifactStore,
                     boolean enabled,
                     int timeoutSeconds,
                     int maxOutputChars) {
        this.sessionSandboxGateway = Objects.requireNonNull(sessionSandboxGateway, "sessionSandboxGateway");
        this.sandboxPathResolver = Objects.requireNonNull(sandboxPathResolver, "sandboxPathResolver");
        this.toolResultArtifactStore = toolResultArtifactStore;
        this.enabled = enabled;
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
        this.maxOutputChars = Math.max(256, maxOutputChars);
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

        String sessionId = MDC.get("sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            return jsonError(command, cwd, "missing sessionId");
        }

        Path workingDir;
        try {
            workingDir = sandboxPathResolver.resolveSandboxPath(cwd == null || cwd.isBlank() ? "." : cwd);
        } catch (SecurityException e) {
            return jsonError(command, cwd, e.getMessage());
        }

        Instant startedAt = Instant.now();
        AiSandboxCommandResult result = sessionSandboxGateway.executeCommand(
                sessionId,
                command,
                workingDir.toString(),
                timeoutSeconds
        );
        String stdout = safeText(result.stdout());
        String stderr = safeText(result.stderr());
        int exitCode = result.exitCode();
        boolean timedOut = exitCode == 124;
        String stdoutPreview = abbreviate(stdout);
        String stderrPreview = abbreviate(stderr);
        boolean truncated = timedOut || stdoutPreview.length() < stdout.length() || stderrPreview.length() < stderr.length();

        String artifactId = null;
        String outputPath = null;
        String preview = null;
        if (truncated) {
            String full = buildFullPayload(command, workingDir.toString(), stdout, stderr, timedOut, exitCode);
            artifactId = saveArtifactIfPossible(memoryId, command, cwd, full);
            if (artifactId == null) {
                outputPath = saveSnapshotFile(sessionId, workingDir, full);
            }
            preview = buildPreview(full, maxOutputChars);
        }

        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("command", command);
        root.put("exitCode", exitCode);
        root.put("cwd", workingDir.toString());
        root.put("stdout", stdoutPreview);
        root.put("stderr", stderrPreview);
        root.put("truncated", truncated);
        root.put("timeoutSeconds", timeoutSeconds);
        root.put("startedAtEpochMs", startedAt.toEpochMilli());
        root.put("finishedAtEpochMs", Instant.now().toEpochMilli());
        if (timedOut) {
            root.put("timedOut", true);
        }
        if (artifactId != null) {
            root.put("artifactId", artifactId);
        }
        if (outputPath != null) {
            root.put("path", outputPath);
        }
        if (preview != null) {
            root.put("preview", preview);
        }
        return root.toString();
    }

    private static String buildFullPayload(String command,
                                           String cwd,
                                           String stdout,
                                           String stderr,
                                           boolean timedOut,
                                           int exitCode) {
        return """
                [Shell Output]
                command=%s
                cwd=%s
                exitCode=%d
                timedOut=%s
                --- stdout ---
                %s
                --- stderr ---
                %s
                """.formatted(command, cwd, exitCode, timedOut, safeText(stdout), safeText(stderr));
    }

    private String saveArtifactIfPossible(Object memoryId, String command, String cwd, String fullPayload) {
        if (toolResultArtifactStore == null || memoryId == null) {
            return null;
        }
        try {
            ObjectNode args = OBJECT_MAPPER.createObjectNode();
            args.put("command", command);
            if (cwd != null && !cwd.isBlank()) {
                args.put("cwd", cwd);
            }
            return toolResultArtifactStore.save(memoryId, "runShellCommand", args.toString(), fullPayload);
        } catch (RuntimeException e) {
            log.warn("Shell output offload failed, fallback to file snapshot: {}", e.getMessage());
            return null;
        }
    }

    private String saveSnapshotFile(String sessionId, Path workingDir, String content) {
        try {
            Path dir = workingDir.resolve(".openmanus").resolve("shell");
            Path file = dir.resolve("shell-output-" + System.currentTimeMillis() + ".txt");
            sessionSandboxGateway.writeTextFile(sessionId, file.toString(), content);
            return file.toString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String buildPreview(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        int limit = Math.max(256, Math.min(maxChars, 8000));
        if (text.length() <= limit) {
            return text;
        }
        int head = Math.min((limit * 2) / 3, text.length());
        int tail = Math.min(limit - head, Math.max(0, text.length() - head));
        return text.substring(0, head) + "\n...\n" + (tail > 0 ? text.substring(text.length() - tail) : "");
    }

    private String abbreviate(String text) {
        if (text.length() <= maxOutputChars) {
            return text;
        }
        return text.substring(0, maxOutputChars);
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
