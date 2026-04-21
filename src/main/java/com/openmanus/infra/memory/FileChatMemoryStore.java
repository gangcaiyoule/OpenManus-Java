package com.openmanus.infra.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

/**
 * File-based chat memory store.
 * One conversation id maps to one JSON file.
 */
@Slf4j
public class FileChatMemoryStore implements ChatMemoryStore, AtomicAppendChatMemoryStore {

    private final Path baseDir;
    private final Path corruptedDir;
    private final Duration retentionDuration;
    private final boolean quarantineCorruptedFiles;
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public FileChatMemoryStore(Path baseDir) {
        this(baseDir, 30, true);
    }

    public FileChatMemoryStore(Path baseDir, int retentionDays, boolean quarantineCorruptedFiles) {
        this.baseDir = baseDir.toAbsolutePath().normalize();
        this.corruptedDir = this.baseDir.resolve("corrupted");
        this.retentionDuration = Duration.ofDays(Math.max(1, retentionDays));
        this.quarantineCorruptedFiles = quarantineCorruptedFiles;
        try {
            Files.createDirectories(this.baseDir);
            if (this.quarantineCorruptedFiles) {
                Files.createDirectories(this.corruptedDir);
            }
            cleanupExpiredFiles();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize chat memory dir: " + this.baseDir, e);
        }
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = memoryKey(memoryId);
        ReentrantLock lock = lockFor(key);
        long startNs = System.nanoTime();
        lock.lock();
        try {
            List<ChatMessage> messages = withProcessLock(key, () -> readMessagesLocked(key));
            log.debug("chat_memory op=get key={} messages={} durationMs={}",
                    key, messages.size(), elapsedMs(startNs));
            return messages;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read chat memory: " + key, e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = memoryKey(memoryId);
        ReentrantLock lock = lockFor(key);
        long startNs = System.nanoTime();
        lock.lock();
        try {
            withProcessLock(key, () -> {
                writeMessagesLocked(key, messages);
                return null;
            });
            log.debug("chat_memory op=update key={} messages={} durationMs={}",
                    key, messages.size(), elapsedMs(startNs));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write chat memory: " + key, e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically append one message under the same per-memory lock.
     */
    public void appendMessage(Object memoryId, ChatMessage message) {
        String key = memoryKey(memoryId);
        ReentrantLock lock = lockFor(key);
        long startNs = System.nanoTime();
        lock.lock();
        try {
            List<ChatMessage> current = withProcessLock(key, () -> {
                List<ChatMessage> messages = readMessagesLocked(key);
                messages.add(message);
                writeMessagesLocked(key, messages);
                return messages;
            });
            log.debug("chat_memory op=append key={} messages={} durationMs={}",
                    key, current.size(), elapsedMs(startNs));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to append chat memory: " + key, e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean appendIfAbsent(Object memoryId, ChatMessage candidate, Predicate<ChatMessage> existsPredicate) {
        String key = memoryKey(memoryId);
        ReentrantLock lock = lockFor(key);
        long startNs = System.nanoTime();
        lock.lock();
        try {
            boolean appended = withProcessLock(key, () -> {
                List<ChatMessage> messages = readMessagesLocked(key);
                if (messages.stream().anyMatch(existsPredicate)) {
                    return false;
                }
                messages.add(candidate);
                writeMessagesLocked(key, messages);
                return true;
            });
            log.debug("chat_memory op=append_if_absent key={} appended={} durationMs={}",
                    key, appended, elapsedMs(startNs));
            return appended;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to append-if-absent chat memory: " + key, e);
        } finally {
            lock.unlock();
        }
    }

    public boolean appendSystemMessageIfAbsent(Object memoryId, SystemMessage systemMessage) {
        return appendIfAbsent(memoryId, systemMessage, message ->
                message instanceof SystemMessage existing && existing.text().equals(systemMessage.text()));
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String key = memoryKey(memoryId);
        ReentrantLock lock = lockFor(key);
        long startNs = System.nanoTime();
        lock.lock();
        try {
            withProcessLock(key, () -> {
                Files.deleteIfExists(fileFor(key));
                Files.deleteIfExists(lockFileFor(key));
                return null;
            });
            log.debug("chat_memory op=delete key={} durationMs={}", key, elapsedMs(startNs));
        } catch (IOException e) {
            log.warn("Failed to delete chat memory {}: {}", key, e.getMessage());
        } finally {
            lock.unlock();
            locks.remove(key, lock);
        }
    }

    private String memoryKey(Object memoryId) {
        if (memoryId == null) {
            throw new IllegalArgumentException("memoryId cannot be null");
        }
        return sha256(String.valueOf(memoryId));
    }

    private Path fileFor(String memoryKey) {
        return baseDir.resolve(memoryKey + ".json").normalize();
    }

    private Path lockFileFor(String memoryKey) {
        return baseDir.resolve(memoryKey + ".lck").normalize();
    }

    private ReentrantLock lockFor(String key) {
        return locks.computeIfAbsent(key, ignored -> new ReentrantLock());
    }

    private <T> T withProcessLock(String key, IoOperation<T> op) throws IOException {
        Path lockFile = lockFileFor(key);
        try (FileChannel channel = FileChannel.open(lockFile,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE)) {
            int attempts = 0;
            while (true) {
                try (FileLock ignored = channel.lock()) {
                    return op.execute();
                } catch (OverlappingFileLockException e) {
                    attempts++;
                    if (attempts >= 200) {
                        throw new IOException("Failed to acquire process lock for key: " + key, e);
                    }
                    try {
                        Thread.sleep(2L);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted while waiting process lock for key: " + key,
                                interruptedException);
                    }
                }
            }
        }
    }

    private List<ChatMessage> readMessagesLocked(String key) throws IOException {
        Path file = fileFor(key);
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        String json = Files.readString(file, StandardCharsets.UTF_8);
        if (json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(ChatMessageDeserializer.messagesFromJson(json));
        } catch (RuntimeException e) {
            quarantineCorruptedFile(key, file, e);
            throw new IllegalStateException("Failed to decode chat memory: " + key, e);
        }
    }

    private void writeMessagesLocked(String key, List<ChatMessage> messages) throws IOException {
        Path file = fileFor(key);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        String json = ChatMessageSerializer.messagesToJson(messages);
        Files.writeString(tmp, json, StandardCharsets.UTF_8);
        moveReplace(tmp, file);
    }

    private void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void quarantineCorruptedFile(String key, Path file, RuntimeException e) {
        if (!quarantineCorruptedFiles) {
            return;
        }
        try {
            if (!Files.exists(file)) {
                return;
            }
            String quarantinedName = file.getFileName() + ".corrupted-" + System.currentTimeMillis();
            Path quarantinedFile = corruptedDir.resolve(quarantinedName);
            Files.move(file, quarantinedFile, StandardCopyOption.REPLACE_EXISTING);
            log.warn("chat_memory op=quarantine key={} file={} quarantined={} reason={}",
                    key, file.getFileName(), quarantinedFile.getFileName(), e.getClass().getSimpleName());
        } catch (IOException moveErr) {
            log.warn("chat_memory op=quarantine_failed key={} file={} reason={}",
                    key, file.getFileName(), moveErr.getMessage());
        }
    }

    private void cleanupExpiredFiles() throws IOException {
        Instant cutoff = Instant.now().minus(retentionDuration);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(baseDir, "*.json")) {
            for (Path file : stream) {
                FileTime lastModifiedTime = Files.getLastModifiedTime(file);
                if (lastModifiedTime.toInstant().isBefore(cutoff)) {
                    Files.deleteIfExists(file);
                    String fileName = file.getFileName().toString();
                    String memoryKey = fileName.substring(0, fileName.length() - ".json".length());
                    Files.deleteIfExists(lockFileFor(memoryKey));
                    log.debug("chat_memory op=cleanup_expired file={} cutoff={}",
                            file.getFileName(), cutoff);
                }
            }
        }
        // Cleanup orphaned/stale lock files.
        try (DirectoryStream<Path> lockFiles = Files.newDirectoryStream(baseDir, "*.lck")) {
            for (Path lockFile : lockFiles) {
                String lockName = lockFile.getFileName().toString();
                String memoryKey = lockName.substring(0, lockName.length() - ".lck".length());
                Path jsonFile = fileFor(memoryKey);
                if (!Files.exists(jsonFile)) {
                    FileTime lastModifiedTime = Files.getLastModifiedTime(lockFile);
                    if (lastModifiedTime.toInstant().isBefore(cutoff)) {
                        Files.deleteIfExists(lockFile);
                        log.debug("chat_memory op=cleanup_orphan_lock file={} cutoff={}",
                                lockFile.getFileName(), cutoff);
                    }
                }
            }
        }
    }

    private static long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @FunctionalInterface
    private interface IoOperation<T> {
        T execute() throws IOException;
    }
}
