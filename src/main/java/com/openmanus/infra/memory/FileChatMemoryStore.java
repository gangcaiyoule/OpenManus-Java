package com.openmanus.infra.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.aiframework.runtime.AiMemoryStore;
import com.openmanus.aiframework.runtime.model.AiChatMessage;
import com.openmanus.aiframework.runtime.model.AiToolCall;
import com.openmanus.aiframework.runtime.model.AiToolResult;
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
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;

/**
 * File-based runtime chat memory store.
 * One conversation id maps to one JSON file.
 */
@Slf4j
public class FileChatMemoryStore implements AiMemoryStore, AtomicAppendChatMemoryStore {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final Path baseDir;
  private final Path corruptedDir;
  private final Duration retentionDuration;
  private final boolean quarantineCorruptedFiles;
  private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

  /**
   * Build store with default retention and corruption handling.
   */
  public FileChatMemoryStore(Path baseDir) {
    this(baseDir, 30, true);
  }

  /**
   * Build store with explicit retention/corruption handling settings.
   */
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
  public List<AiChatMessage> getMessages(Object memoryId) {
    String key = memoryKey(memoryId);
    ReentrantLock lock = lockFor(key);
    long startNs = System.nanoTime();
    lock.lock();
    try {
      List<AiChatMessage> messages = withProcessLock(key, () -> readMessagesLocked(key));
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
  public void updateMessages(Object memoryId, List<AiChatMessage> messages) {
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
          key, messages == null ? 0 : messages.size(), elapsedMs(startNs));
    } catch (IOException e) {
      throw new IllegalStateException("Failed to write chat memory: " + key, e);
    } finally {
      lock.unlock();
    }
  }

  /**
   * Atomically append one message under the same per-memory lock.
   */
  @Override
  public void append(Object memoryId, AiChatMessage message) {
    Objects.requireNonNull(message, "message");
    String key = memoryKey(memoryId);
    ReentrantLock lock = lockFor(key);
    long startNs = System.nanoTime();
    lock.lock();
    try {
      List<AiChatMessage> current = withProcessLock(key, () -> {
        List<AiChatMessage> messages = readMessagesLocked(key);
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
  public boolean appendIfAbsent(
      Object memoryId,
      AiChatMessage candidate,
      Predicate<AiChatMessage> existsPredicate) {
    Objects.requireNonNull(candidate, "candidate");
    Objects.requireNonNull(existsPredicate, "existsPredicate");
    String key = memoryKey(memoryId);
    ReentrantLock lock = lockFor(key);
    long startNs = System.nanoTime();
    lock.lock();
    try {
      boolean appended = withProcessLock(key, () -> {
        List<AiChatMessage> messages = readMessagesLocked(key);
        if (messages.stream().filter(Objects::nonNull).anyMatch(existsPredicate)) {
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

  /**
   * Atomically append one system message if an equivalent message does not exist.
   */
  public boolean appendSystemMessageIfAbsent(Object memoryId, String systemText) {
    if (systemText == null || systemText.isBlank()) {
      return false;
    }
    return appendIfAbsent(memoryId, AiChatMessage.system(systemText), message ->
        message.role() == AiChatMessage.Role.SYSTEM
            && Objects.equals(message.content(), systemText));
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

  private List<AiChatMessage> readMessagesLocked(String key) throws IOException {
    Path file = fileFor(key);
    if (!Files.exists(file)) {
      return new ArrayList<>();
    }
    String json = Files.readString(file, StandardCharsets.UTF_8);
    if (json.isBlank()) {
      return new ArrayList<>();
    }
    try {
      List<AiChatMessage> runtimeMessages = parseRuntimeMessages(json);
      if (runtimeMessages != null) {
        return new ArrayList<>(runtimeMessages);
      }
      List<AiChatMessage> legacyMessages = parseLegacyMessages(json);
      if (legacyMessages != null) {
        return new ArrayList<>(legacyMessages);
      }
      throw new IllegalStateException("Unsupported chat memory JSON format");
    } catch (RuntimeException e) {
      quarantineCorruptedFile(key, file, e);
      throw new IllegalStateException("Failed to decode chat memory: " + key, e);
    }
  }

  private List<AiChatMessage> parseRuntimeMessages(String json) {
    JsonNode root;
    try {
      root = OBJECT_MAPPER.readTree(json);
    } catch (JsonProcessingException e) {
      return null;
    }

    JsonNode messageNode = root;
    if (root.isObject() && root.has("messages")) {
      messageNode = root.get("messages");
    }
    if (!messageNode.isArray()) {
      return null;
    }

    List<AiChatMessage> runtimeMessages = new ArrayList<>();
    for (JsonNode node : messageNode) {
      if (node == null || node.isNull()) {
        continue;
      }
      if (!node.isObject() || !node.has("role")) {
        return null;
      }
      runtimeMessages.add(OBJECT_MAPPER.convertValue(node, AiChatMessage.class));
    }
    return runtimeMessages;
  }

  private List<AiChatMessage> parseLegacyMessages(String json) {
    JsonNode root;
    try {
      root = OBJECT_MAPPER.readTree(json);
    } catch (JsonProcessingException e) {
      return null;
    }
    if (!root.isArray()) {
      return null;
    }

    List<AiChatMessage> messages = new ArrayList<>();
    for (JsonNode node : root) {
      if (node.isNull()) {
        continue;
      }
      if (!node.isObject()) {
        return null;
      }
      String type = node.path("type").asText("").trim().toUpperCase();
      if (type.isEmpty()) {
        if (looksLikeRuntimeMessageNode(node)) {
          return null;
        }
        continue;
      }
      switch (type) {
        case "USER" -> messages.add(AiChatMessage.user(extractLegacyText(node)));
        case "SYSTEM" -> messages.add(AiChatMessage.system(extractLegacyText(node)));
        case "AI" -> {
          List<AiToolCall> toolCalls = parseLegacyToolCalls(node.path("toolExecutionRequests"));
          messages.add(AiChatMessage.assistant(extractLegacyText(node), toolCalls));
        }
        case "TOOL_EXECUTION_RESULT" -> {
          AiChatMessage toolMessage = parseLegacyToolResult(node);
          if (toolMessage != null) {
            messages.add(toolMessage);
          }
        }
        default -> {
          // Keep compatibility with legacy behavior: unsupported entries are skipped.
        }
      }
    }
    return messages;
  }

  private boolean looksLikeRuntimeMessageNode(JsonNode node) {
    return node.has("role")
        || node.has("content")
        || node.has("toolCalls")
        || node.has("toolCallId");
  }

  private List<AiToolCall> parseLegacyToolCalls(JsonNode toolCallsNode) {
    if (!toolCallsNode.isArray()) {
      return List.of();
    }
    List<AiToolCall> toolCalls = new ArrayList<>();
    for (JsonNode toolCallNode : toolCallsNode) {
      if (!toolCallNode.isObject()) {
        continue;
      }
      String name = textOrNull(toolCallNode, "name");
      if (name == null || name.isBlank()) {
        continue;
      }
      String id = textOrNull(toolCallNode, "id");
      JsonNode argumentsNode = toolCallNode.get("arguments");
      String arguments = argumentsNode == null
          ? "{}"
          : (argumentsNode.isTextual() ? argumentsNode.asText("{}") : argumentsNode.toString());
      toolCalls.add(new AiToolCall(id, name, arguments));
    }
    return toolCalls;
  }

  private AiChatMessage parseLegacyToolResult(JsonNode node) {
    String toolName = firstNonBlank(
        textOrNull(node, "toolName"),
        textOrNull(node, "name")
    );
    String toolCallId = firstNonBlank(
        textOrNull(node, "id"),
        textOrNull(node, "toolExecutionRequestId"),
        textOrNull(node, "toolExecutionId")
    );
    if (toolName == null) {
      return null;
    }
    if (toolCallId == null) {
      boolean hasLegacyIdField = node.has("id")
          || node.has("toolExecutionRequestId")
          || node.has("toolExecutionId");
      if (hasLegacyIdField) {
        return null;
      }
      toolCallId = "legacy_" + sha256(toolName + "\n" + extractLegacyText(node));
    }
    if (toolCallId.isBlank()) {
      return null;
    }
    String content = extractLegacyText(node);
    return AiChatMessage.tool(new AiToolResult(toolCallId, toolName, content, false));
  }

  private String extractLegacyText(JsonNode node) {
    JsonNode textNode = node.get("text");
    if (textNode != null && !textNode.isNull()) {
      return textNode.asText("");
    }
    if (!node.path("contents").isArray()) {
      return "";
    }
    JsonNode contentsNode = node.path("contents");
    StringBuilder joined = new StringBuilder();
    for (JsonNode content : contentsNode) {
      if (!content.isObject()) {
        continue;
      }
      JsonNode partText = content.get("text");
      if (partText != null && !partText.isNull()) {
        joined.append(partText.asText(""));
      }
    }
    return joined.toString();
  }

  private String textOrNull(JsonNode node, String fieldName) {
    JsonNode field = node.get(fieldName);
    if (field == null || field.isNull()) {
      return null;
    }
    return field.asText(null);
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value.trim();
      }
    }
    return null;
  }

  private void writeMessagesLocked(String key, List<AiChatMessage> messages) throws IOException {
    Path file = fileFor(key);
    Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
    String json = OBJECT_MAPPER.writeValueAsString(messages == null ? List.of() : messages);
    Files.writeString(tmp, json, StandardCharsets.UTF_8);
    moveReplace(tmp, file);
  }

  private void moveReplace(Path source, Path target) throws IOException {
    try {
      Files.move(
          source,
          target,
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
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
        String memoryKey = memoryKeyFromPath(file, ".json");
        if (memoryKey == null) {
          continue;
        }
        FileTime lastModifiedTime = Files.getLastModifiedTime(file);
        if (lastModifiedTime.toInstant().isBefore(cutoff)) {
          Files.deleteIfExists(file);
          Files.deleteIfExists(lockFileFor(memoryKey));
          log.debug("chat_memory op=cleanup_expired file={} cutoff={}",
              file.getFileName(), cutoff);
        }
      }
    }
    try (DirectoryStream<Path> lockFiles = Files.newDirectoryStream(baseDir, "*.lck")) {
      for (Path lockFile : lockFiles) {
        String memoryKey = memoryKeyFromPath(lockFile, ".lck");
        if (memoryKey == null) {
          continue;
        }
        Path jsonFile = fileFor(memoryKey);
        if (!Files.exists(jsonFile)) {
          FileTime lastModifiedTime = Files.getLastModifiedTime(lockFile);
          if (lastModifiedTime.toInstant().isBefore(cutoff)) {
            Files.deleteIfExists(lockFile);
            log.debug(
                "chat_memory op=cleanup_orphan_lock file={} cutoff={}",
                lockFile.getFileName(),
                cutoff);
          }
        }
      }
    }
  }

  static String memoryKeyFromPath(Path file, String suffix) {
    if (file == null || suffix == null || suffix.isEmpty()) {
      return null;
    }
    Path fileNamePath = file.getFileName();
    if (fileNamePath == null) {
      return null;
    }
    String fileName = fileNamePath.toString();
    if (!fileName.endsWith(suffix) || fileName.length() <= suffix.length()) {
      return null;
    }
    return fileName.substring(0, fileName.length() - suffix.length());
  }

  private static long elapsedMs(long startNs) {
    return (System.nanoTime() - startNs) / 1_000_000L;
  }

  private static String sha256(String input) {
    return sha256(input, "SHA-256");
  }

  private static String sha256(String input, String algorithm) {
    try {
      MessageDigest digest = MessageDigest.getInstance(algorithm);
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(algorithm + " not available", e);
    }
  }

  @FunctionalInterface
  private interface IoOperation<T> {
    T execute() throws IOException;
  }
}
