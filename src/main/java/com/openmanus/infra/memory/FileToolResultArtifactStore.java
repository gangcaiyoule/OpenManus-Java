package com.openmanus.infra.memory;

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * File-based lossless artifact store for tool results.
 * The payload is gzip-compressed and addressed by SHA-256 of original text content.
 */
@Slf4j
public class FileToolResultArtifactStore implements ToolResultArtifactStore {

    private static final int DEFAULT_MAX_INDEX_ENTRIES_PER_MEMORY = 20000;
    private final Path baseDir;
    private final Path indexDir;
    private final int maxIndexEntriesPerMemory;
    private static final int INDEX_LOCK_STRIPES = 256;
    private static final ReentrantLock[] INDEX_LOCKS = createIndexLocks();

    public FileToolResultArtifactStore(Path baseDir) {
        this(baseDir, DEFAULT_MAX_INDEX_ENTRIES_PER_MEMORY);
    }

    public FileToolResultArtifactStore(Path baseDir, int maxIndexEntriesPerMemory) {
        this.baseDir = baseDir.toAbsolutePath().normalize();
        this.indexDir = this.baseDir.resolve("_index");
        this.maxIndexEntriesPerMemory = Math.max(1, maxIndexEntriesPerMemory);
        try {
            Files.createDirectories(this.baseDir);
            Files.createDirectories(this.indexDir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize tool artifact dir: " + this.baseDir, e);
        }
    }

    @Override
    public String save(Object memoryId, String toolName, String toolArguments, String outcome) {
        if (outcome == null) {
            outcome = "";
        }
        String hash = sha256Hex(outcome);
        String artifactId = "sha256:" + hash;
        Path targetDir = baseDir.resolve(hash.substring(0, 2)).resolve(hash.substring(2, 4));
        Path targetFile = targetDir.resolve(hash + ".txt.gz");
        if (Files.exists(targetFile)) {
            appendIndex(memoryId, artifactId, toolName, toolArguments, outcome.length());
            return artifactId;
        }
        try {
            Files.createDirectories(targetDir);
            byte[] compressed = gzip(outcome);
            Path tmp = targetFile.resolveSibling(targetFile.getFileName() + ".tmp");
            Files.write(tmp, compressed);
            moveReplace(tmp, targetFile);
            appendIndex(memoryId, artifactId, toolName, toolArguments, outcome.length());
            log.debug("tool_artifact op=save id={} memoryId={} tool={} bytes={}",
                    artifactId, String.valueOf(memoryId), toolName, compressed.length);
            return artifactId;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist tool-result artifact " + artifactId, e);
        }
    }

    @Override
    public Optional<String> load(String artifactId) {
        String hash = parseArtifactHash(artifactId);
        if (hash == null) {
            return Optional.empty();
        }
        Path targetFile = fileForHash(hash);
        if (!Files.exists(targetFile)) {
            return Optional.empty();
        }
        try {
            byte[] compressed = Files.readAllBytes(targetFile);
            return Optional.of(ungzip(compressed));
        } catch (IOException e) {
            log.warn("tool_artifact op=load_failed id={} reason={}", artifactId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<ArtifactRef> recent(Object memoryId, int limit) {
        int safeLimit = Math.max(0, limit);
        if (safeLimit == 0 || memoryId == null) {
            return List.of();
        }
        Path file = indexFile(memoryId);
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            List<String> lines = readLastLines(file, safeLimit * 4);
            List<ArtifactRef> refs = new ArrayList<>();
            for (int i = lines.size() - 1; i >= 0 && refs.size() < safeLimit; i--) {
                String line = lines.get(i);
                ArtifactRef parsed = parseIndexLine(line);
                if (parsed != null) {
                    refs.add(parsed);
                }
            }
            return refs;
        } catch (IOException e) {
            log.warn("tool_artifact op=index_load_failed memoryId={} reason={}", String.valueOf(memoryId), e.getMessage());
            return List.of();
        }
    }

    private static List<String> readLastLines(Path file, int maxLines) throws IOException {
        if (maxLines <= 0) {
            return List.of();
        }
        Deque<String> lastLines = new ArrayDeque<>(maxLines);
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long length = raf.length();
            if (length == 0) {
                return List.of();
            }
            long pointer = length - 1;
            ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream(256);
            while (pointer >= 0 && lastLines.size() < maxLines) {
                raf.seek(pointer);
                int value = raf.read();
                if (value == '\n') {
                    appendLineIfNotBlank(lastLines, lineBuffer, maxLines);
                } else if (value != '\r') {
                    lineBuffer.write(value);
                }
                pointer--;
            }
            appendLineIfNotBlank(lastLines, lineBuffer, maxLines);
        }
        return new ArrayList<>(lastLines);
    }

    private static void appendLineIfNotBlank(Deque<String> lines,
                                             ByteArrayOutputStream reverseBytes,
                                             int maxLines) {
        if (reverseBytes.size() == 0) {
            return;
        }
        byte[] reversed = reverseBytes.toByteArray();
        byte[] normal = new byte[reversed.length];
        for (int i = 0; i < reversed.length; i++) {
            normal[i] = reversed[reversed.length - 1 - i];
        }
        String line = new String(normal, StandardCharsets.UTF_8);
        reverseBytes.reset();
        if (line.isBlank()) {
            return;
        }
        if (lines.size() == maxLines) {
            lines.removeFirst();
        }
        lines.addFirst(line);
    }

    private static byte[] gzip(String text) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
            gzip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to gzip tool-result artifact", e);
        }
    }

    private static String ungzip(byte[] compressed) throws IOException {
        try (ByteArrayInputStream in = new ByteArrayInputStream(compressed);
             GZIPInputStream gzipIn = new GZIPInputStream(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = gzipIn.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path fileForHash(String hash) {
        return baseDir.resolve(hash.substring(0, 2))
                .resolve(hash.substring(2, 4))
                .resolve(hash + ".txt.gz");
    }

    private static String parseArtifactHash(String artifactId) {
        if (artifactId == null) {
            return null;
        }
        if (!artifactId.startsWith("sha256:")) {
            return null;
        }
        String hash = artifactId.substring("sha256:".length());
        if (hash.length() != 64) {
            return null;
        }
        for (int i = 0; i < hash.length(); i++) {
            char c = hash.charAt(i);
            boolean hex = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!hex) {
                return null;
            }
        }
        return hash.toLowerCase();
    }

    private void appendIndex(Object memoryId,
                             String artifactId,
                             String toolName,
                             String toolArguments,
                             int originalChars) {
        if (memoryId == null || artifactId == null) {
            return;
        }
        Path file = indexFile(memoryId);
        String line = toIndexLine(artifactId, toolName, toolArguments, originalChars, System.currentTimeMillis());
        ReentrantLock lock = lockForMemory(memoryId);
        lock.lock();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.WRITE,
                    java.nio.file.StandardOpenOption.APPEND);
            pruneIndexIfNeeded(file);
        } catch (IOException e) {
            log.warn("tool_artifact op=index_append_failed memoryId={} id={} reason={}",
                    String.valueOf(memoryId), artifactId, e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private void pruneIndexIfNeeded(Path file) throws IOException {
        if (maxIndexEntriesPerMemory <= 0 || !Files.exists(file)) {
            return;
        }
        boolean alwaysPruneForSmallCap = maxIndexEntriesPerMemory <= 1000;
        if (!alwaysPruneForSmallCap) {
            long fileSize = Files.size(file);
            long pruneThresholdBytes = Math.max(16384L, (long) maxIndexEntriesPerMemory * 96L);
            if (fileSize <= pruneThresholdBytes) {
                return;
            }
        }
        List<String> kept = readLastLines(file, maxIndexEntriesPerMemory);
        if (kept.isEmpty()) {
            return;
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        String content = String.join(System.lineSeparator(), kept) + System.lineSeparator();
        Files.writeString(tmp, content, StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.WRITE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        moveReplace(tmp, file);
    }

    private Path indexFile(Object memoryId) {
        String memoryHash = sha256Hex(String.valueOf(memoryId));
        return indexDir.resolve(memoryHash.substring(0, 2))
                .resolve(memoryHash + ".log");
    }

    private static String toIndexLine(String artifactId,
                                      String toolName,
                                      String toolArguments,
                                      int originalChars,
                                      long createdAtEpochMs) {
        return escape(artifactId) + "\t"
                + escape(nullToEmpty(toolName)) + "\t"
                + escape(nullToEmpty(toolArguments)) + "\t"
                + originalChars + "\t"
                + createdAtEpochMs;
    }

    private static ArtifactRef parseIndexLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] parts = line.split("\t", -1);
        if (parts.length < 5) {
            return null;
        }
        try {
            String artifactId = unescape(parts[0]);
            int originalChars = Integer.parseInt(parts[3]);
            long createdAt = Long.parseLong(parts[4]);
            if (parseArtifactHash(artifactId) == null) {
                return null;
            }
            return new ArtifactRef(
                    artifactId,
                    unescape(parts[1]),
                    unescape(parts[2]),
                    originalChars,
                    createdAt
            );
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String unescape(String value) {
        StringBuilder result = new StringBuilder(value.length());
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!escaped) {
                if (c == '\\') {
                    escaped = true;
                } else {
                    result.append(c);
                }
                continue;
            }
            switch (c) {
                case 't' -> result.append('\t');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case '\\' -> result.append('\\');
                default -> result.append(c);
            }
            escaped = false;
        }
        if (escaped) {
            result.append('\\');
        }
        return result.toString();
    }

    private static ReentrantLock lockForMemory(Object memoryId) {
        String key = String.valueOf(memoryId);
        int index = Math.floorMod(key.hashCode(), INDEX_LOCK_STRIPES);
        return INDEX_LOCKS[index];
    }

    private static ReentrantLock[] createIndexLocks() {
        ReentrantLock[] locks = new ReentrantLock[INDEX_LOCK_STRIPES];
        for (int i = 0; i < INDEX_LOCK_STRIPES; i++) {
            locks[i] = new ReentrantLock();
        }
        return locks;
    }
}
