package com.openmanus.infra.memory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory ToolResultArtifactStore for local runtime/testing usage.
 */
public class InMemoryToolResultArtifactStore implements ToolResultArtifactStore {

  private final ConcurrentHashMap<String, String> payloads = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, ConcurrentLinkedDeque<ArtifactRef>> recentByMemory =
      new ConcurrentHashMap<>();
  private final int maxIndexEntriesPerMemory;

  public InMemoryToolResultArtifactStore(int maxIndexEntriesPerMemory) {
    this.maxIndexEntriesPerMemory = Math.max(1, maxIndexEntriesPerMemory);
  }

  @Override
  public String save(Object memoryId, String toolName, String toolArguments, String outcome) {
    String safeOutcome = outcome == null ? "" : outcome;
    String artifactId = "sha256:" + sha256Hex(safeOutcome);
    payloads.put(artifactId, safeOutcome);
    if (memoryId != null) {
      String key = String.valueOf(memoryId);
      ConcurrentLinkedDeque<ArtifactRef> refs =
          recentByMemory.computeIfAbsent(key, ignored -> new ConcurrentLinkedDeque<>());
      refs.addFirst(new ArtifactRef(
          artifactId,
          toolName,
          toolArguments,
          safeOutcome.length(),
          System.currentTimeMillis()));
      while (refs.size() > maxIndexEntriesPerMemory) {
        refs.pollLast();
      }
    }
    return artifactId;
  }

  private static String sha256Hex(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  @Override
  public Optional<String> load(String artifactId) {
    if (artifactId == null || artifactId.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(payloads.get(artifactId));
  }

  @Override
  public List<ArtifactRef> recent(Object memoryId, int limit) {
    if (memoryId == null || limit <= 0) {
      return List.of();
    }
    ConcurrentLinkedDeque<ArtifactRef> refs = recentByMemory.get(String.valueOf(memoryId));
    if (refs == null || refs.isEmpty()) {
      return List.of();
    }
    List<ArtifactRef> result = new ArrayList<>(Math.min(limit, refs.size()));
    int count = 0;
    for (ArtifactRef ref : refs) {
      if (count >= limit) {
        break;
      }
      result.add(ref);
      count++;
    }
    return result;
  }
}
