package com.openmanus.domain.service;

import java.nio.file.Path;

/**
 * 会话级文件沙盒目录提供者。
 */
public interface SessionFileSandboxDirectoryProvider {

    Path getOrCreateFileSandboxRoot(String sessionId);
}
