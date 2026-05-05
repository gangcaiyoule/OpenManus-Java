package com.openmanus.infra.log;

import com.openmanus.aiframework.runtime.AiLogMarkers;
import org.slf4j.Marker;

/**
 * 日志标记常量
 * 用于标识需要推送到前端的重要日志
 */
public class LogMarkers {
    
    /**
     * 标记需要推送到前端的重要日志
     * 使用方式: log.info(TO_FRONTEND, "重要消息", args)
     */
    public static final Marker TO_FRONTEND = AiLogMarkers.TO_FRONTEND;
    
    private LogMarkers() {
        // 私有构造函数，防止实例化
    }
} 
