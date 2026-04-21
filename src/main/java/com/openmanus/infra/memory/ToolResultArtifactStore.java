package com.openmanus.infra.memory;

import java.util.List;
import java.util.Optional;

/**
 * Store for lossless tool-result artifacts.
 * Large tool outputs can be offloaded out of model/chat-memory payloads while keeping
 * retrievable raw content.
 */
public interface ToolResultArtifactStore {

  /**
   * Metadata pointer for one persisted tool-result artifact.
   */
  final class ArtifactRef {
    private final String artifactId;
    private final String toolName;
    private final String toolArguments;
    private final int originalChars;
    private final long createdAtEpochMs;

    /**
     * Build one artifact metadata reference.
     */
    public ArtifactRef(String artifactId,
                       String toolName,
                       String toolArguments,
                       int originalChars,
                       long createdAtEpochMs) {
      this.artifactId = artifactId;
      this.toolName = toolName;
      this.toolArguments = toolArguments;
      this.originalChars = originalChars;
      this.createdAtEpochMs = createdAtEpochMs;
    }

    public String artifactId() {
      return artifactId;
    }

    public String toolName() {
      return toolName;
    }

    public String toolArguments() {
      return toolArguments;
    }

    public int originalChars() {
      return originalChars;
    }

    public long createdAtEpochMs() {
      return createdAtEpochMs;
    }
  }

  /**
   * Persist a tool result artifact and return artifact id.
   */
  String save(Object memoryId, String toolName, String toolArguments, String outcome);

  /**
   * Load raw artifact text by id.
   * Returns empty when id is missing, malformed, or unreadable.
   */
  Optional<String> load(String artifactId);

  /**
   * Returns recent artifact references for one memory id, newest first.
   * Default implementation returns empty when store does not support indexing.
   */
  default List<ArtifactRef> recent(Object memoryId, int limit) {
    return List.of();
  }
}
