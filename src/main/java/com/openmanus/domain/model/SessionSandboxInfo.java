package com.openmanus.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话沙箱信息模型。
 *
 * 只保留会话级编排所需快照，不暴露容器 ID、端口等运行时细节。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionSandboxInfo {
    
    /**
     * 会话 ID
     */
    private String sessionId;
    
    /**
     * VNC Web 访问 URL
     * 前端通过 iframe 嵌入此 URL 来展示浏览器工作台
     */
    private String vncUrl;

    /**
     * 沙箱创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 沙箱状态
     */
    private SandboxStatus status;
    
    /**
     * 沙箱状态枚举
     */
    public enum SandboxStatus {
        /** 正在创建 */
        CREATING,
        /** 运行中 */
        RUNNING,
        /** 已停止 */
        STOPPED,
        /** 错误 */
        ERROR
    }
    
    /**
     * 检查沙箱是否可用
     */
    public boolean isAvailable() {
        return status == SandboxStatus.RUNNING && vncUrl != null && !vncUrl.isEmpty();
    }
}
