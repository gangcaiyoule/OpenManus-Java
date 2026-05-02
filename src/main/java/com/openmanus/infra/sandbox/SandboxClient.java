package com.openmanus.infra.sandbox;

import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.openmanus.infra.config.OpenManusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Strongly-enforced Docker session sandbox client.
 */
public class SandboxClient implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(SandboxClient.class);

    private final DockerClientManager dockerManager;
    private final OpenManusProperties.SandboxSettings config;
    private final Map<String, SessionContainer> sessionContainers = new ConcurrentHashMap<>();

    public SandboxClient(OpenManusProperties properties) {
        this(properties, new DockerClientManager());
    }

    SandboxClient(OpenManusProperties properties, DockerClientManager dockerManager) {
        if (properties == null || properties.getSandbox() == null) {
            throw new IllegalArgumentException("sandbox properties cannot be null");
        }
        this.config = properties.getSandbox();
        this.dockerManager = Objects.requireNonNull(dockerManager, "dockerManager");
        this.dockerManager.pullImageIfNeeded(config.getImage());
    }

    public SessionContainer ensureSessionContainer(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId cannot be blank");
        }
        SessionContainer existing = sessionContainers.get(sessionId);
        if (existing != null && dockerManager.isContainerRunning(existing.containerId())) {
            return existing;
        }
        synchronized (sessionContainers) {
            SessionContainer current = sessionContainers.get(sessionId);
            if (current != null && dockerManager.isContainerRunning(current.containerId())) {
                return current;
            }
            SessionContainer created = createSessionContainer(sessionId);
            sessionContainers.put(sessionId, created);
            return created;
        }
    }

    public boolean isSessionRunning(String sessionId) {
        SessionContainer container = sessionContainers.get(sessionId);
        return container != null && dockerManager.isContainerRunning(container.containerId());
    }

    public String getWorkspaceRoot() {
        return config.getWorkDir();
    }

    public String getContainerId(String sessionId) {
        SessionContainer container = sessionContainers.get(sessionId);
        return container == null ? null : container.containerId();
    }

    public ExecutionResult executeCommand(String sessionId, String command, String cwd, int timeoutSeconds) {
        SessionContainer container = ensureSessionContainer(sessionId);
        String wrappedCommand = buildWrappedShellCommand(cwd, command);
        return executeInContainer(container.containerId(), wrappedCommand, timeoutSeconds);
    }

    public ExecutionResult executePython(String sessionId, String script, int timeoutSeconds) {
        String command = "python3 -c " + escapeShellArgument(script);
        return executeCommand(sessionId, command, null, timeoutSeconds);
    }

    public String readTextFile(String sessionId, String path) {
        String script = """
                from pathlib import Path
                print(Path(%s).read_text(encoding='utf-8'), end='')
                """.formatted(toPythonStringLiteral(path));
        ExecutionResult result = executeInContainer(
                ensureSessionContainer(sessionId).containerId(),
                "python3 - <<'PY'\n" + script + "\nPY",
                config.getTimeout());
        if (!result.isSuccess()) {
            throw new RuntimeException("读取沙箱文件失败: " + result.getStderr());
        }
        return result.getStdout();
    }

    public void writeTextFile(String sessionId, String path, String content) {
        String encoded = java.util.Base64.getEncoder().encodeToString(
                (content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
        String script = """
                from pathlib import Path
                import base64
                file_path = Path(%s)
                file_path.parent.mkdir(parents=True, exist_ok=True)
                file_path.write_text(base64.b64decode(%s).decode('utf-8'), encoding='utf-8')
                """.formatted(toPythonStringLiteral(path), toPythonStringLiteral(encoded));
        ExecutionResult result = executeInContainer(
                ensureSessionContainer(sessionId).containerId(),
                "python3 - <<'PY'\n" + script + "\nPY",
                config.getTimeout());
        if (!result.isSuccess()) {
            throw new RuntimeException("写入沙箱文件失败: " + result.getStderr());
        }
    }

    public void destroySessionContainer(String sessionId) {
        SessionContainer container = sessionContainers.remove(sessionId);
        if (container == null) {
            return;
        }
        dockerManager.destroyContainer(container.containerId());
    }

    private SessionContainer createSessionContainer(String sessionId) {
        String containerName = "openmanus-session-" + sessionId.toLowerCase().replaceAll("[^a-z0-9_-]", "-");
        log.info("创建会话 Docker 沙箱: sessionId={}, image={}", sessionId, config.getImage());
        CreateContainerResponse container = dockerManager.getClient()
                .createContainerCmd(config.getImage())
                .withName(containerName + "-" + Instant.now().toEpochMilli())
                .withWorkingDir(config.getWorkDir())
                .withHostConfig(HostConfig.newHostConfig()
                        .withMemory(DockerClientManager.parseMemoryLimit(config.getMemoryLimit()))
                        .withCpuQuota((long) (config.getCpuLimit() * 100000))
                        .withCpuPeriod(100000L)
                        .withNetworkMode(config.isNetworkEnabled() ? "bridge" : "none")
                        .withAutoRemove(false))
                .withCmd("sh", "-lc", "mkdir -p " + escapeShellArgument(config.getWorkDir()) + " && tail -f /dev/null")
                .exec();
        String containerId = container.getId();
        dockerManager.getClient().startContainerCmd(containerId).exec();
        dockerManager.waitForContainerReady(containerId, Math.max(5, config.getTimeout()));
        return new SessionContainer(sessionId, containerId, config.getWorkDir());
    }

    private ExecutionResult executeInContainer(String containerId, String command, int timeoutSeconds) {
        try {
            ExecCreateCmdResponse execCmd = dockerManager.getClient()
                    .execCreateCmd(containerId)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd("/bin/sh", "-lc", command)
                    .exec();

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            @SuppressWarnings("deprecation")
            ExecStartResultCallback callback = new ExecStartResultCallback(stdout, stderr);
            dockerManager.getClient().execStartCmd(execCmd.getId()).exec(callback);

            int timeout = timeoutSeconds > 0 ? timeoutSeconds : config.getTimeout();
            boolean completed = callback.awaitCompletion(timeout, TimeUnit.SECONDS);
            if (!completed) {
                return new ExecutionResult(
                        stdout.toString(StandardCharsets.UTF_8),
                        stderr.toString(StandardCharsets.UTF_8) + "\n执行超时",
                        124
                );
            }

            InspectExecResponse execResponse = dockerManager.getClient().inspectExecCmd(execCmd.getId()).exec();
            Integer exitCode = execResponse.getExitCodeLong() == null ? 0 : execResponse.getExitCodeLong().intValue();
            return new ExecutionResult(
                    stdout.toString(StandardCharsets.UTF_8),
                    stderr.toString(StandardCharsets.UTF_8),
                    exitCode
            );
        } catch (Exception e) {
            log.error("会话容器执行失败: {}", e.getMessage(), e);
            return new ExecutionResult("", "沙箱执行失败: " + e.getMessage(), 1);
        }
    }

    private String buildWrappedShellCommand(String cwd, String command) {
        String effectiveCommand = command == null ? "" : command;
        String effectiveCwd = cwd == null || cwd.isBlank() ? config.getWorkDir() : cwd;
        return "cd " + escapeShellArgument(effectiveCwd) + " && " + effectiveCommand;
    }

    private static String escapeShellArgument(String arg) {
        return "'" + (arg == null ? "" : arg.replace("'", "'\"'\"'")) + "'";
    }

    private static String toPythonStringLiteral(String value) {
        String normalized = value == null ? "" : value;
        return "'" + normalized
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                + "'";
    }

    @Override
    public void close() throws IOException {
        for (String sessionId : sessionContainers.keySet()) {
            destroySessionContainer(sessionId);
        }
        dockerManager.close();
    }

    public record SessionContainer(String sessionId, String containerId, String workspaceRoot) {
    }
}
