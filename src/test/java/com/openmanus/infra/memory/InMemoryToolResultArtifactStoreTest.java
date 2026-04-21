package com.openmanus.infra.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryToolResultArtifactStoreTest {

  @Test
  void shouldUseSha256ArtifactIdAndDeduplicatePayload() {
    InMemoryToolResultArtifactStore store = new InMemoryToolResultArtifactStore(10);

    String first = store.save("conv", "search", "{\"q\":\"first\"}", "same-result");
    String second = store.save("conv", "search", "{\"q\":\"second\"}", "same-result");

    assertTrue(first.startsWith("sha256:"));
    assertEquals(71, first.length(), "artifactId should be sha256:<64-hex>");
    assertEquals(first, second, "same payload should reuse same artifactId");
    assertEquals("same-result", store.load(first).orElseThrow());
  }

  @Test
  void shouldKeepRecentIndexEntriesEvenWhenPayloadIsDeduplicated() {
    InMemoryToolResultArtifactStore store = new InMemoryToolResultArtifactStore(3);

    String id = store.save("conv", "search", "{\"q\":\"1\"}", "same-result");
    store.save("conv", "search", "{\"q\":\"2\"}", "same-result");
    store.save("conv", "search", "{\"q\":\"3\"}", "same-result");

    List<ToolResultArtifactStore.ArtifactRef> recent = store.recent("conv", 5);
    assertEquals(3, recent.size(), "recent index should respect configured limit");
    assertEquals(id, recent.get(0).artifactId());
    assertEquals(id, recent.get(1).artifactId());
    assertEquals(id, recent.get(2).artifactId());
  }
}

