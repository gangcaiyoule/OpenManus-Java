package com.openmanus.domain.service;

import com.openmanus.domain.model.SessionSandboxInfo;
import com.openmanus.infra.config.OpenManusProperties;
import com.openmanus.infra.sandbox.VncSandboxClient;
import com.openmanus.infra.sandbox.VncSandboxInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 会话沙箱管理器
 *
 * 职责：
 * 1. 管理 sessionId 与沙箱容器的映射关系
 * 2. 按需创建沙箱（首次调用浏览器工具时）
 * 3. 提供沙箱信息查询接口
 * 4. 定期清理过期的沙箱容器
 *
 * 设计模式：
 * - 单例模式：全局唯一的会话管理器
 * - 工厂模式：创建沙箱实例
 * - 缓存模式：内存缓存会话-沙箱映射
 */
@Service
@Slf4j
public class SessionSandboxManager {

    private final VncSandboxClient vncSandboxClient;

    // 会话沙箱映射表 - 线程安全
    private final Map<String, SessionSandboxInfo> sessionSandboxMap = new ConcurrentHashMap<>();
    private static final int MAX_WARNED_LEGACY_MAPPINGS = 2048;
    private static final int DEFAULT_LEGACY_MAPPING_WARN_SAMPLE_RATE = 200;
    private final boolean legacyMappingWarnEnabled;
    private final int legacyMappingWarnSampleRate;
    private final Map<String, Boolean> warnedLegacyMappings = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_WARNED_LEGACY_MAPPINGS;
                }
            });
    private final AtomicLong legacyMappingCounter = new AtomicLong(0);

    // 沙箱超时时间（小时）
    private static final int SANDBOX_TIMEOUT_HOURS = 2;
    private static final Path FILE_SANDBOX_BASE_DIR = Paths.get(
            System.getProperty("java.io.tmpdir"), "openmanus", "file-sandboxes");
    private static final Path FILE_SANDBOX_BASE_DIR_ABS = FILE_SANDBOX_BASE_DIR.toAbsolutePath().normalize();
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    public SessionSandboxManager(VncSandboxClient vncSandboxClient) {
        this(vncSandboxClient, new OpenManusProperties());
    }

    @Autowired
    public SessionSandboxManager(VncSandboxClient vncSandboxClient, OpenManusProperties properties) {
        this.vncSandboxClient = vncSandboxClient;
        OpenManusProperties.LegacyMappingConfig config = properties == null ? null : properties.getLegacyMapping();
        this.legacyMappingWarnEnabled = config != null && config.isWarnEnabled();
        this.legacyMappingWarnSampleRate = config == null
                ? DEFAULT_LEGACY_MAPPING_WARN_SAMPLE_RATE
                : config.getWarnSampleRate();
        log.info("SessionSandboxManager 初始化完成");
    }

    /**
     * 获取会话的沙箱信息（如果存在）
     *
     * @param sessionId 会话 ID
     * @return 沙箱信息，不存在则返回 empty
     */
    public Optional<SessionSandboxInfo> getSandboxInfo(String sessionId) {
        SessionSandboxInfo info = sessionSandboxMap.get(sessionId);

        if (info != null) {
            // 验证容器是否仍在运行
            if (!vncSandboxClient.isContainerRunning(info.getContainerId())) {
                log.warn("会话 {} 的沙箱容器已停止，更新状态", sessionId);
                info.setStatus(SessionSandboxInfo.SandboxStatus.STOPPED);
            }
        }

        return Optional.ofNullable(info);
    }

    /**
     * 为会话创建或获取沙箱
     *
     * 说明：
     * - VNC 沙箱容器侧沿用原始 sessionId 作为关联键，保证与现有容器管理逻辑兼容。
     * - 文件沙箱目录侧会对 sessionId 做安全规范化（见 getOrCreateFileSandboxRoot）。
     *   两者处理策略不同是有意设计：容器映射追求兼容，文件路径追求边界安全。
     *
     * @param sessionId 会话 ID
     * @return 沙箱信息
     */
    public synchronized SessionSandboxInfo getOrCreateSandbox(String sessionId) {
        // 检查是否已存在
        SessionSandboxInfo existing = sessionSandboxMap.get(sessionId);
        if (existing != null && existing.isAvailable()) {
            log.debug("复用现有沙箱: sessionId={}, vncUrl={}", sessionId, existing.getVncUrl());
            return existing;
        }

        // 创建新沙箱
        log.info("为会话 {} 创建新的 VNC 沙箱", sessionId);

        try {
            // 标记为创建中
            SessionSandboxInfo creatingInfo = SessionSandboxInfo.builder()
                .sessionId(sessionId)
                .status(SessionSandboxInfo.SandboxStatus.CREATING)
                .createdAt(LocalDateTime.now())
                .build();
            sessionSandboxMap.put(sessionId, creatingInfo);

            // 调用 VncSandboxClient 创建容器
            VncSandboxInfo vncInfo = vncSandboxClient.createVncSandbox(sessionId);

            // 构建会话沙箱信息
            SessionSandboxInfo sandboxInfo = SessionSandboxInfo.builder()
                .sessionId(sessionId)
                .containerId(vncInfo.getContainerId())
                .vncUrl(vncInfo.getVncUrl())
                .mappedPort(vncInfo.getMappedPort())
                .status(SessionSandboxInfo.SandboxStatus.RUNNING)
                .createdAt(LocalDateTime.now())
                .build();

            // 存入映射表
            sessionSandboxMap.put(sessionId, sandboxInfo);

            log.info("会话 {} 的 VNC 沙箱创建成功: {}", sessionId, sandboxInfo.getVncUrl());
            return sandboxInfo;

        } catch (Exception e) {
            log.error("创建 VNC 沙箱失败: sessionId={}", sessionId, e);

            // 标记为错误状态
            SessionSandboxInfo errorInfo = SessionSandboxInfo.builder()
                .sessionId(sessionId)
                .status(SessionSandboxInfo.SandboxStatus.ERROR)
                .createdAt(LocalDateTime.now())
                .build();
            sessionSandboxMap.put(sessionId, errorInfo);

            throw new RuntimeException("创建 VNC 沙箱失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取会话级文件沙盒根目录，不存在则创建。
     * 文件工具必须只在该目录内进行读写。
     *
     * @param sessionId 会话 ID
     * @return 会话沙盒根目录
     */
    public Path getOrCreateFileSandboxRoot(String sessionId) {
        String safeSessionId = toSafeFileSandboxSessionId(sessionId);
        Path sessionRoot = FILE_SANDBOX_BASE_DIR_ABS.resolve(safeSessionId).normalize();
        if (!sessionRoot.startsWith(FILE_SANDBOX_BASE_DIR_ABS)) {
            throw new SecurityException("非法会话ID导致沙盒路径越界: " + sessionId);
        }
        try {
            Files.createDirectories(sessionRoot);
            return sessionRoot;
        } catch (IOException e) {
            throw new RuntimeException("创建会话文件沙盒目录失败: " + sessionRoot, e);
        }
    }

    private String toSafeFileSandboxSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new SecurityException("缺少会话ID，拒绝创建文件沙盒目录");
        }
        if (SESSION_ID_PATTERN.matcher(sessionId).matches()) {
            return sessionId;
        }
        String hashed = sha256Hex(sessionId);
        String mapped = "legacy-" + hashed;
        if (markLegacyMappingIfFirstSeen(mapped)) {
            long index = legacyMappingCounter.incrementAndGet();
            if (shouldWarnLegacyMapping(index)) {
                log.warn("会话ID不符合文件沙盒命名规则，已映射为安全目录名: {}", mapped);
            } else {
                log.debug("会话ID不符合文件沙盒命名规则，已映射为安全目录名: {}", mapped);
            }
        } else {
            log.debug("复用 legacy 会话目录映射: {}", mapped);
        }
        return mapped;
    }

    private boolean shouldWarnLegacyMapping(long index) {
        if (!legacyMappingWarnEnabled) {
            return false;
        }
        int rate = Math.max(1, legacyMappingWarnSampleRate);
        return index % rate == 0;
    }

    static int resolveLegacyMappingWarnSampleRate(String raw) {
        return resolveLegacyMappingWarnSampleRateWithMeta(raw).value();
    }

    static boolean isLegacyMappingWarnSampleRateInvalid(String raw) {
        return resolveLegacyMappingWarnSampleRateWithMeta(raw).invalid();
    }

    private static SampleRateResolution resolveLegacyMappingWarnSampleRateWithMeta(String raw) {
        if (raw == null || raw.isBlank()) {
            return new SampleRateResolution(DEFAULT_LEGACY_MAPPING_WARN_SAMPLE_RATE, false);
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed > 0) {
                return new SampleRateResolution(parsed, false);
            }
            return new SampleRateResolution(DEFAULT_LEGACY_MAPPING_WARN_SAMPLE_RATE, true);
        } catch (NumberFormatException e) {
            return new SampleRateResolution(DEFAULT_LEGACY_MAPPING_WARN_SAMPLE_RATE, true);
        }
    }

    private record SampleRateResolution(int value, boolean invalid) {
    }

    private boolean markLegacyMappingIfFirstSeen(String mapped) {
        synchronized (warnedLegacyMappings) {
            if (warnedLegacyMappings.containsKey(mapped)) {
                warnedLegacyMappings.put(mapped, Boolean.TRUE);
                return false;
            }
            warnedLegacyMappings.put(mapped, Boolean.TRUE);
            return true;
        }
    }

    int warnedLegacyMappingsSizeForTest() {
        synchronized (warnedLegacyMappings) {
            return warnedLegacyMappings.size();
        }
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /**
     * 销毁会话的沙箱
     *
     * @param sessionId 会话 ID
     */
    public void destroySandbox(String sessionId) {
        SessionSandboxInfo info = sessionSandboxMap.remove(sessionId);

        if (info != null && info.getContainerId() != null) {
            try {
                log.info("销毁会话 {} 的沙箱容器: {}", sessionId, info.getContainerId());
                vncSandboxClient.destroyVncSandbox(info.getContainerId());
            } catch (Exception e) {
                log.error("销毁沙箱失败: sessionId={}, containerId={}",
                    sessionId, info.getContainerId(), e);
            }
        }
    }

    /**
     * 定期清理过期的沙箱容器
     * 每 30 分钟执行一次
     */
    @Scheduled(fixedRate = 30 * 60 * 1000)  // 30 分钟
    public void cleanupExpiredSandboxes() {
        log.info("开始清理过期的沙箱容器");

        LocalDateTime now = LocalDateTime.now();
        int cleanedCount = 0;

        for (Map.Entry<String, SessionSandboxInfo> entry : sessionSandboxMap.entrySet()) {
            SessionSandboxInfo info = entry.getValue();

            if (info.getCreatedAt() != null) {
                long hours = ChronoUnit.HOURS.between(info.getCreatedAt(), now);

                // 清理超过 2 小时的沙箱
                if (hours >= SANDBOX_TIMEOUT_HOURS) {
                    log.info("清理过期沙箱: sessionId={}, 运行时间={}小时",
                        entry.getKey(), hours);
                    destroySandbox(entry.getKey());
                    cleanedCount++;
                }
            }
        }

        if (cleanedCount > 0) {
            log.info("清理完成，共清理 {} 个过期沙箱", cleanedCount);
        } else {
            log.debug("无需清理，所有沙箱都在有效期内");
        }
    }

    /**
     * 应用关闭时清理所有沙箱
     */
    @PreDestroy
    public void cleanup() {
        log.info("应用关闭，清理所有沙箱容器");

        for (String sessionId : sessionSandboxMap.keySet()) {
            destroySandbox(sessionId);
        }

        log.info("所有沙箱已清理完成");
    }
}
