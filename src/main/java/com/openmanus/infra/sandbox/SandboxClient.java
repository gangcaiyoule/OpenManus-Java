package com.openmanus.infra.sandbox;

import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.openmanus.infra.config.OpenManusProperties;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
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
        SessionContainer container = ensureSessionContainer(sessionId);
        return executeInContainer(
                container.containerId(),
                "python3 -",
                timeoutSeconds,
                stdinOf(script)
        );
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
        SessionContainer container = ensureSessionContainer(sessionId);
        String normalizedPath = normalizeContainerPath(path);
        String remoteDir = remoteDirOf(normalizedPath);
        String fileName = fileNameOf(normalizedPath);

        ExecutionResult mkdirResult = executeInContainer(
                container.containerId(),
                "mkdir -p " + escapeShellArgument(remoteDir),
                config.getTimeout()
        );
        if (!mkdirResult.isSuccess()) {
            throw new RuntimeException("写入沙箱文件失败: " + mkdirResult.getStderr());
        }

        try {
            dockerManager.getClient()
                    .copyArchiveToContainerCmd(container.containerId())
                    .withRemotePath(remoteDir)
                    .withTarInputStream(singleFileTarArchive(fileName, content))
                    .exec();
        } catch (Exception e) {
            throw new RuntimeException("写入沙箱文件失败: " + e.getMessage(), e);
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
        return executeInContainer(containerId, command, timeoutSeconds, null);
    }

    private ExecutionResult executeInContainer(String containerId,
                                              String command,
                                              int timeoutSeconds,
                                              InputStream stdin) {
        try {
            ExecCreateCmdResponse execCmd = dockerManager.getClient()
                    .execCreateCmd(containerId)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withAttachStdin(stdin != null)
                    .withCmd("/bin/sh", "-lc", command)
                    .exec();

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            @SuppressWarnings("deprecation")
            ExecStartResultCallback callback = new ExecStartResultCallback(stdout, stderr);
            var execStart = dockerManager.getClient().execStartCmd(execCmd.getId());
            if (stdin != null) {
                execStart.withStdIn(stdin);
            }
            execStart.exec(callback);

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

    private static InputStream stdinOf(String value) {
        return new ByteArrayInputStream((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String normalizeContainerPath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path cannot be blank");
        }
        return path;
    }

    private static String remoteDirOf(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0) {
            return ".";
        }
        if (lastSlash == 0) {
            return "/";
        }
        return path.substring(0, lastSlash);
    }

    private static String fileNameOf(String path) {
        int lastSlash = path.lastIndexOf('/');
        String fileName = lastSlash < 0 ? path : path.substring(lastSlash + 1);
        if (fileName.isBlank()) {
            throw new IllegalArgumentException("path must include a file name");
        }
        return fileName;
    }

    private static InputStream singleFileTarArchive(String fileName, String content) throws IOException {
        byte[] bytes = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(output)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            TarArchiveEntry entry = new TarArchiveEntry(fileName);
            entry.setSize(bytes.length);
            tar.putArchiveEntry(entry);
            tar.write(bytes);
            tar.closeArchiveEntry();
            tar.finish();
        }
        return new ByteArrayInputStream(output.toByteArray());
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
