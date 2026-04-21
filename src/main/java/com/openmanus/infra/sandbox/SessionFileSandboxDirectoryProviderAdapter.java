package com.openmanus.infra.sandbox;

import com.openmanus.domain.service.LegacySessionMappingPolicy;
import com.openmanus.domain.service.SessionFileSandboxDirectoryProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

@Component
@Slf4j
public class SessionFileSandboxDirectoryProviderAdapter implements SessionFileSandboxDirectoryProvider {

    private static final int MAX_WARNED_LEGACY_MAPPINGS = 2048;
    private static final int DEFAULT_LEGACY_MAPPING_WARN_SAMPLE_RATE = 200;
    private static final Path FILE_SANDBOX_BASE_DIR = Paths.get(
            System.getProperty("java.io.tmpdir"), "openmanus", "file-sandboxes");
    private static final Path FILE_SANDBOX_BASE_DIR_ABS = FILE_SANDBOX_BASE_DIR.toAbsolutePath().normalize();
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

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

    public SessionFileSandboxDirectoryProviderAdapter(LegacySessionMappingPolicy policy) {
        this.legacyMappingWarnEnabled = policy != null && policy.warnEnabled();
        this.legacyMappingWarnSampleRate = policy == null
                ? DEFAULT_LEGACY_MAPPING_WARN_SAMPLE_RATE
                : policy.warnSampleRate();
    }

    @Override
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

    int warnedLegacyMappingsSizeForTest() {
        synchronized (warnedLegacyMappings) {
            return warnedLegacyMappings.size();
        }
    }

    private String toSafeFileSandboxSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new SecurityException("缺少会话ID，拒绝创建文件沙盒目录");
        }
        if (SESSION_ID_PATTERN.matcher(sessionId).matches()) {
            return sessionId;
        }
        String mapped = "legacy-" + sha256Hex(sessionId);
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
}
