package com.openmanus.infra.sandbox;

import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.openmanus.infra.config.OpenManusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * VNC 图形界面沙箱客户端
 * 
 * 功能：
 * 1. 提供带桌面环境和浏览器的 Docker 容器
 * 2. 通过 noVNC 提供 Web 访问接口
 * 3. 支持按需创建和销毁（每个 session 独立容器）
 * 
 * 设计：工厂模式，支持多实例
 */
@Component
public class VncSandboxClient implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(VncSandboxClient.class);
    
    // VNC 配置常量
    private static final String VNC_IMAGE = "dorowu/ubuntu-desktop-lxde-vnc:latest";
    private static final int VNC_WEB_PORT = 6080;
    private static final String VNC_RESOLUTION = "1280x720";
    private static final String VNC_PASSWORD = "openmanus";
    
    private final DockerClientManager dockerManager;
    private final String hostAddress;
    
    public VncSandboxClient(OpenManusProperties properties) {
        this.dockerManager = new DockerClientManager();
        this.hostAddress = resolveHostAddress();
        
        log.info("VNC 沙箱客户端初始化成功，主机地址: {}", hostAddress);
    }
    
    /**
     * 创建 VNC 沙箱
     * 
     * @param sessionId 会话标识符（用于容器命名）
     * @return VNC 沙箱信息（包含容器 ID 和访问 URL）
     */
    public VncSandboxInfo createVncSandbox(String sessionId) {
        ensureEnabled();
        try {
            log.info("创建 VNC 沙箱，会话ID: {}", sessionId);
            
            // 拉取镜像
            dockerManager.pullImageIfNeeded(VNC_IMAGE);
            
            // 创建容器
            String containerName = "vnc-sandbox-" + sessionId.toLowerCase().replaceAll("[^a-z0-9_-]", "-")
                    + "-" + System.currentTimeMillis();
            CreateContainerResponse container = dockerManager.getClient()
                .createContainerCmd(VNC_IMAGE)
                .withName(containerName)
                .withEnv(
                    "RESOLUTION=" + VNC_RESOLUTION,
                    "VNC_PASSWORD=" + VNC_PASSWORD,
                    "HTTP_PASSWORD=" + VNC_PASSWORD
                )
                .withHostConfig(HostConfig.newHostConfig()
                    // 端口映射：容器 6080 -> 宿主机随机端口
                    .withPortBindings(new PortBinding(
                        Ports.Binding.empty(),
                        new ExposedPort(VNC_WEB_PORT)
                    ))
                    // 资源限制（VNC 需要更多资源）
                    .withMemory(DockerClientManager.parseMemoryLimit("1g"))
                    .withCpuQuota(200000L)  // 2 CPU cores
                    .withCpuPeriod(100000L)
                    .withNetworkMode("bridge")
                    .withAutoRemove(false)
                    // 共享内存（Chrome 需要）
                    .withShmSize(512L * 1024 * 1024)  // 512MB
                )
                .withExposedPorts(new ExposedPort(VNC_WEB_PORT))
                .exec();
            
            String containerId = container.getId();
            
            // 启动容器
            dockerManager.getClient().startContainerCmd(containerId).exec();
            log.info("VNC 容器启动成功: {}", containerId);
            
            // 等待容器就绪
            dockerManager.waitForContainerReady(containerId, 10);
            
            // 获取映射端口
            int mappedPort = dockerManager.getContainerMappedPort(containerId, VNC_WEB_PORT);
            
            // 生成访问 URL
            String vncUrl = String.format(
                    "http://%s:%d/vnc.html?autoconnect=true&resize=remote&password=%s",
                    hostAddress,
                    mappedPort,
                    VNC_PASSWORD
            );
            
            VncSandboxInfo sandboxInfo = new VncSandboxInfo(containerId, vncUrl, mappedPort);
            log.info("VNC 沙箱创建完成: {}", sandboxInfo);
            
            return sandboxInfo;
            
        } catch (Exception e) {
            log.error("创建 VNC 沙箱失败: {}", e.getMessage(), e);
            throw new RuntimeException("VNC 沙箱创建失败", e);
        }
    }
    
    /**
     * 销毁 VNC 沙箱
     */
    public void destroyVncSandbox(String containerId) {
        if (dockerManager == null) {
            return;
        }
        dockerManager.destroyContainer(containerId);
    }
    
    /**
     * 检查容器是否运行
     */
    public boolean isContainerRunning(String containerId) {
        if (dockerManager == null) {
            return false;
        }
        return dockerManager.isContainerRunning(containerId);
    }

    public ExecutionResult openBrowserUrl(String containerId, String url) {
        if (containerId == null || containerId.isBlank()) {
            return new ExecutionResult("", "VNC containerId is blank", 1);
        }
        if (url == null || url.isBlank()) {
            return new ExecutionResult("", "URL is blank", 1);
        }
        String command = """
                export URL=%s
                export DISPLAY=${DISPLAY:-:1}
                nohup sh -lc '
                  for browser in chromium-browser chromium google-chrome firefox xdg-open; do
                    if command -v "$browser" >/dev/null 2>&1; then
                      if [ "$browser" = "xdg-open" ]; then
                        exec "$browser" "$URL"
                      fi
                      exec "$browser" --no-sandbox "$URL"
                    fi
                  done
                  echo "no browser found" >&2
                  exit 127
                ' >/tmp/openmanus-browser.log 2>&1 &
                echo "browser-open-started"
                """.formatted(escapeShellArgument(url));
        return executeInContainer(containerId, command, 8);
    }
    
    /**
     * 解析宿主机地址
     */
    private String resolveHostAddress() {
        try {
            // 优先使用环境变量
            String envHost = System.getenv("OPENMANUS_HOST_ADDRESS");
            if (envHost != null && !envHost.isEmpty()) {
                return envHost;
            }
            
            // 默认给本机浏览器可访问的地址，避免局域网/主机名解析导致 iframe 空白。
            return "localhost";
        } catch (Exception e) {
            log.warn("无法获取宿主机地址，使用 localhost: {}", e.getMessage());
            return "localhost";
        }
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
            boolean completed = callback.awaitCompletion(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                return new ExecutionResult(
                        stdout.toString(StandardCharsets.UTF_8),
                        stderr.toString(StandardCharsets.UTF_8) + "\n执行超时",
                        124
                );
            }
            Long exitCode = dockerManager.getClient()
                    .inspectExecCmd(execCmd.getId())
                    .exec()
                    .getExitCodeLong();
            return new ExecutionResult(
                    stdout.toString(StandardCharsets.UTF_8),
                    stderr.toString(StandardCharsets.UTF_8),
                    exitCode == null ? 0 : exitCode.intValue()
            );
        } catch (Exception e) {
            log.error("VNC 容器执行失败: {}", e.getMessage(), e);
            return new ExecutionResult("", "VNC 容器执行失败: " + e.getMessage(), 1);
        }
    }

    private static String escapeShellArgument(String value) {
        return "'" + (value == null ? "" : value.replace("'", "'\"'\"'")) + "'";
    }
    
    @Override
    public void close() throws IOException {
        if (dockerManager != null) {
            dockerManager.close();
            log.info("VNC 沙箱客户端已关闭");
        }
    }

    private void ensureEnabled() {
        if (dockerManager == null) {
            throw new IllegalStateException("VNC 沙箱未初始化 Docker 客户端");
        }
    }
}
