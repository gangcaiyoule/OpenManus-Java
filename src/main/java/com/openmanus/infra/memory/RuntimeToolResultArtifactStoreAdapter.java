package com.openmanus.infra.memory;

import com.openmanus.aiframework.runtime.AiToolResultArtifactStore;
import com.openmanus.aiframework.runtime.ToolResultArtifactRef;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Adapts infra artifact store to runtime abstraction.
 */
public class RuntimeToolResultArtifactStoreAdapter implements AiToolResultArtifactStore {

  private final ToolResultArtifactStore delegate;

  public RuntimeToolResultArtifactStoreAdapter(ToolResultArtifactStore delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
  }

  @Override
  public String save(Object memoryId, String toolName, String toolArguments, String outcome) {
    return delegate.save(memoryId, toolName, toolArguments, outcome);
  }

  @Override
  public Optional<String> load(String artifactId) {
    return delegate.load(artifactId);
  }

  @Override
  public List<ToolResultArtifactRef> recent(Object memoryId, int limit) {
    List<ToolResultArtifactStore.ArtifactRef> refs = delegate.recent(memoryId, limit);
    if (refs == null || refs.isEmpty()) {
      return List.of();
    }
    return refs.stream()
        .filter(Objects::nonNull)
        .map(ref -> new ToolResultArtifactRef(
            ref.artifactId(),
            ref.toolName(),
            ref.toolArguments(),
            ref.originalChars(),
            ref.createdAtEpochMs()
        ))
        .toList();
  }
}
