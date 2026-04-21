package com.openmanus.aiframework.runtime;

import java.util.List;
import java.util.Optional;

/**
 * Runtime abstraction for tool-result artifact persistence and lookup.
 */
public interface AiToolResultArtifactStore {

  /**
   * Persist one tool-result artifact and return artifact id.
   */
  String save(Object memoryId, String toolName, String toolArguments, String outcome);

  /**
   * Load raw artifact text by id.
   */
  Optional<String> load(String artifactId);

  /**
   * Returns recent artifact refs for one memory id, newest first.
   */
  default List<ToolResultArtifactRef> recent(Object memoryId, int limit) {
    return List.of();
  }
}
