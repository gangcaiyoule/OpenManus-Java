package com.openmanus.infra.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Minimal .env loader for local development.
 * Existing non-blank environment variables and system properties always win.
 */
public final class DotenvLoader {

    private static final Logger log = LoggerFactory.getLogger(DotenvLoader.class);

    private DotenvLoader() {
    }

    public static void loadFromWorkingDirectory() {
        load(Path.of(".env"));
    }

    static void load(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return;
        }
        log.info("DotenvLoader: Loading .env from {}", path.toAbsolutePath());
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String rawLine : lines) {
                String line = rawLine == null ? "" : rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("export ")) {
                    line = line.substring("export ".length()).trim();
                }
                int separatorIndex = line.indexOf('=');
                if (separatorIndex <= 0) {
                    continue;
                }
                String key = line.substring(0, separatorIndex).trim();
                String value = normalizeValue(line.substring(separatorIndex + 1).trim());
                if (key.isEmpty()) {
                    continue;
                }
                if (hasNonBlankOverride(key)) {
                    log.debug("DotenvLoader: Skipping {} (already set via env/sysprop)", key);
                    continue;
                }
                System.setProperty(key, value);
                log.info("DotenvLoader: Set {}={}", key, key.contains("KEY") || key.contains("SECRET") ? "***" : value);
            }
        } catch (IOException e) {
            log.warn("Failed to load .env file from {}", path.toAbsolutePath(), e);
        }
    }

    private static String normalizeValue(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        char first = trimmed.charAt(0);
        if (first == '"' || first == '\'') {
            int closingIndex = trimmed.indexOf(first, 1);
            if (closingIndex > 0) {
                String trailing = trimmed.substring(closingIndex + 1).trim();
                if (trailing.isEmpty() || trailing.startsWith("#")) {
                    return trimmed.substring(1, closingIndex);
                }
            }
        }

        if (trimmed.startsWith("#")) {
            return "";
        }

        int inlineCommentIndex = trimmed.indexOf(" #");
        if (inlineCommentIndex >= 0) {
            return trimmed.substring(0, inlineCommentIndex).trim();
        }
        return trimmed;
    }

    private static boolean hasNonBlankOverride(String key) {
        return hasNonBlankValue(System.getenv(key)) || hasNonBlankValue(System.getProperty(key));
    }

    private static boolean hasNonBlankValue(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
