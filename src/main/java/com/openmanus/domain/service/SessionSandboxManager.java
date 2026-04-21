package com.openmanus.domain.service;

import com.openmanus.domain.model.SessionSandboxInfo;
import lombok.extern.slf4j.Slf4j;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话级沙箱编排服务。
 *
 * 职责：
 * 1. 管理 sessionId 与会话沙箱快照的映射关系。
 * 2. 按需创建会话沙箱。
 * 3. 提供会话沙箱信息查询接口。
 * 4. 定期清理过期的会话沙箱快照。
 */
@Slf4j
public class SessionSandboxManager {

    private final SessionSandboxClient sessionSandboxClient;
    private final SessionFileSandboxDirectoryProvider fileSandboxDirectoryProvider;

    // 会话沙箱映射表
    private final Map<String, SessionSandboxInfo> sessionSandboxMap = new ConcurrentHashMap<>();

    // 会话沙箱超时时间（小时）
    private static final int SANDBOX_TIMEOUT_HOURS = 2;

    public SessionSandboxManager(
            SessionSandboxClient sessionSandboxClient,
            SessionFileSandboxDirectoryProvider fileSandboxDirectoryProvider) {
        this.sessionSandboxClient = sessionSandboxClient;
        this.fileSandboxDirectoryProvider = fileSandboxDirectoryProvider;
        log.info("SessionSandboxManager 初始化完成");
    }

    /**
     * 获取会话的沙箱快照信息（如果存在）。
     *
     * @param sessionId 会话 ID
     * @return 沙箱信息，不存在则返回 empty
     */
    public Optional<SessionSandboxInfo> getSandboxInfo(String sessionId) {
        SessionSandboxInfo info = sessionSandboxMap.get(sessionId);
        if (info == null) {
            return Optional.empty();
        }
        try {
            SessionSandboxInfo refreshedInfo = sessionSandboxClient.refreshSandboxInfo(sessionId, info);
            if (refreshedInfo == null) {
                log.warn("刷新会话沙箱快照返回空结果，保留现有快照: sessionId={}, status={}",
                        sessionId, info.getStatus());
                return Optional.of(info);
            }
            sessionSandboxMap.put(sessionId, refreshedInfo);
            return Optional.of(refreshedInfo);
        } catch (RuntimeException e) {
            log.warn("刷新会话沙箱快照失败，保留现有快照: sessionId={}, status={}",
                    sessionId, info.getStatus(), e);
            return Optional.of(info);
        }
    }

    /**
     * 为会话创建或获取沙箱快照。
     *
     * 说明：
     * - 会话级运行时入口继续沿用原始 sessionId 作为关联键。
     * - 文件沙箱目录侧会对 sessionId 做安全规范化（见 getOrCreateFileSandboxRoot）。
     * - 两者处理策略不同是有意设计：运行时入口追求兼容，文件路径追求边界安全。
     *
     * @param sessionId 会话 ID
     * @return 沙箱信息
     */
    public synchronized SessionSandboxInfo getOrCreateSandbox(String sessionId) {
        SessionSandboxInfo existing = sessionSandboxMap.get(sessionId);
        if (existing != null && existing.isAvailable()) {
            log.debug("复用现有会话沙箱快照: sessionId={}, status={}", sessionId, existing.getStatus());
            return existing;
        }

        log.info("为会话 {} 创建新的会话沙箱", sessionId);

        try {
            SessionSandboxInfo creatingInfo = SessionSandboxInfo.builder()
                .sessionId(sessionId)
                .status(SessionSandboxInfo.SandboxStatus.CREATING)
                .createdAt(LocalDateTime.now())
                .build();
            sessionSandboxMap.put(sessionId, creatingInfo);

            SessionSandboxInfo sandboxInfo = sessionSandboxClient.createSandbox(sessionId);
            if (sandboxInfo == null) {
                throw new IllegalStateException("会话沙箱创建返回空结果");
            }
            sandboxInfo.setStatus(SessionSandboxInfo.SandboxStatus.RUNNING);
            sandboxInfo.setCreatedAt(LocalDateTime.now());

            sessionSandboxMap.put(sessionId, sandboxInfo);

            log.info("会话 {} 的会话沙箱创建成功", sessionId);
            return sandboxInfo;

        } catch (Exception e) {
            log.error("创建会话沙箱失败: sessionId={}", sessionId, e);

            SessionSandboxInfo errorInfo = SessionSandboxInfo.builder()
                .sessionId(sessionId)
                .status(SessionSandboxInfo.SandboxStatus.ERROR)
                .createdAt(LocalDateTime.now())
                .build();
            sessionSandboxMap.put(sessionId, errorInfo);

            throw new RuntimeException("创建会话沙箱失败: " + e.getMessage(), e);
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
        return fileSandboxDirectoryProvider.getOrCreateFileSandboxRoot(sessionId);
    }

    /**
     * 销毁会话沙箱。
     *
     * @param sessionId 会话 ID
     */
    public void destroySandbox(String sessionId) {
        SessionSandboxInfo info = sessionSandboxMap.remove(sessionId);

        if (info != null) {
            try {
                log.info("销毁会话 {} 的会话沙箱", sessionId);
                sessionSandboxClient.destroySandbox(sessionId);
            } catch (Exception e) {
                log.error("销毁会话沙箱失败: sessionId={}", sessionId, e);
            }
        }
    }

    /**
     * 定期清理过期的会话沙箱。
     */
    public void cleanupExpiredSandboxes() {
        log.info("开始清理过期的会话沙箱");

        LocalDateTime now = LocalDateTime.now();
        int cleanedCount = 0;

        for (Map.Entry<String, SessionSandboxInfo> entry : sessionSandboxMap.entrySet()) {
            SessionSandboxInfo info = entry.getValue();

            if (info.getCreatedAt() != null) {
                long hours = ChronoUnit.HOURS.between(info.getCreatedAt(), now);

                if (hours >= SANDBOX_TIMEOUT_HOURS) {
                    log.info("清理过期会话沙箱: sessionId={}, 运行时间={}小时",
                        entry.getKey(), hours);
                    destroySandbox(entry.getKey());
                    cleanedCount++;
                }
            }
        }

        if (cleanedCount > 0) {
            log.info("清理完成，共清理 {} 个过期沙箱", cleanedCount);
        } else {
            log.debug("无需清理，所有会话沙箱都在有效期内");
        }
    }

    /**
     * 应用关闭时清理所有会话沙箱。
     */
    public void cleanup() {
        log.info("应用关闭，清理所有会话沙箱");

        for (String sessionId : sessionSandboxMap.keySet()) {
            destroySandbox(sessionId);
        }

        log.info("所有会话沙箱已清理完成");
    }
}
