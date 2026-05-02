package com.openmanus.sandbox.infra;

import com.openmanus.domain.service.LegacySessionMappingPolicy;
import com.openmanus.infra.config.OpenManusProperties;
import com.openmanus.sandbox.domain.port.SandboxWorkspacePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

@Component
@Slf4j
public class SandboxWorkspaceAdapter implements SandboxWorkspacePort {

    private static final int MAX_WARNED_LEGACY_MAPPINGS = 2048;
    private static final int DEFAULT_LEGACY_MAPPING_WARN_SAMPLE_RATE = 200;
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    private final boolean legacyMappingWarnEnabled;
    private final int legacyMappingWarnSampleRate;
    private final Path workspaceRoot;
    private final Map<String, Boolean> warnedLegacyMappings = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_WARNED_LEGACY_MAPPINGS;
                }
            });
    private final AtomicLong legacyMappingCounter = new AtomicLong(0);

    public SandboxWorkspaceAdapter(LegacySessionMappingPolicy policy, OpenManusProperties properties) {
        this.legacyMappingWarnEnabled = policy != null && policy.warnEnabled();
        this.legacyMappingWarnSampleRate = policy == null
                ? DEFAULT_LEGACY_MAPPING_WARN_SAMPLE_RATE
                : policy.warnSampleRate();
        String configuredRoot = properties != null && properties.getSandbox() != null
                ? properties.getSandbox().getWorkDir()
                : "/workspace";
        this.workspaceRoot = Paths.get(configuredRoot == null || configuredRoot.isBlank() ? "/workspace" : configuredRoot)
                .normalize();
    }

    @Override
    public String getWorkspaceRoot(String sessionId) {
        requireSafeSessionId(sessionId);
        return workspaceRoot.toString();
    }

    @Override
    public String resolveWorkspacePath(String sessionId, String userPath) {
        requireSafeSessionId(sessionId);
        String candidate = userPath == null || userPath.isBlank() ? "." : userPath;
        Path relativeCandidate = Paths.get(candidate);
        Path resolved = relativeCandidate.isAbsolute()
                ? workspaceRoot.resolve(relativeCandidate.toString().replaceFirst("^[/\\\\]+", ""))
                : workspaceRoot.resolve(relativeCandidate);
        resolved = resolved.normalize();
        if (!resolved.startsWith(workspaceRoot)) {
            throw new SecurityException("禁止访问沙盒外路径: " + userPath);
        }
        return resolved.toString();
    }

    int warnedLegacyMappingsSizeForTest() {
        synchronized (warnedLegacyMappings) {
            return warnedLegacyMappings.size();
        }
    }

    private String requireSafeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new SecurityException("缺少会话ID，拒绝访问会话沙盒");
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
