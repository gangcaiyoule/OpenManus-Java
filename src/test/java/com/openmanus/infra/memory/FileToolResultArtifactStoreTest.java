package com.openmanus.infra.memory;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileToolResultArtifactStoreTest {

    @Test
    void shouldSaveLoadAndQueryRecentByMemoryId() throws Exception {
        Path dir = Files.createTempDirectory("tool-artifacts-");
        FileToolResultArtifactStore store = new FileToolResultArtifactStore(dir);

        String first = store.save("mem-1", "search", "{\"q\":\"a\"}", "result-a");
        String second = store.save("mem-1", "search", "{\"q\":\"b\"}", "result-b");
        store.save("mem-2", "search", "{\"q\":\"c\"}", "result-c");

        assertTrue(store.load(first).isPresent());
        assertEquals("result-a", store.load(first).orElseThrow());
        assertTrue(store.load(second).isPresent());
        assertEquals("result-b", store.load(second).orElseThrow());

        List<ToolResultArtifactStore.ArtifactRef> recent = store.recent("mem-1", 2);
        assertEquals(2, recent.size(), "应返回该会话最近两条记录");
        assertEquals(second, recent.get(0).artifactId(), "recent 返回应按时间倒序");
        assertEquals(first, recent.get(1).artifactId(), "较早记录应排在后面");
        assertEquals("search", recent.get(0).toolName());
        assertFalse(recent.get(0).toolArguments().isBlank());
    }

    @Test
    void shouldAppendIndexEvenWhenArtifactPayloadIsDeduplicated() throws Exception {
        Path dir = Files.createTempDirectory("tool-artifacts-dedup-");
        FileToolResultArtifactStore store = new FileToolResultArtifactStore(dir);

        String first = store.save("mem-dedup", "search", "{\"q\":\"first\"}", "same-result");
        String second = store.save("mem-dedup", "search", "{\"q\":\"second\"}", "same-result");

        assertEquals(first, second, "同内容应复用同一 artifactId");
        List<ToolResultArtifactStore.ArtifactRef> recent = store.recent("mem-dedup", 2);
        assertEquals(2, recent.size(), "即使 payload 复用，索引也应保留两次会话引用");
        assertEquals(second, recent.get(0).artifactId());
        assertTrue(recent.get(0).toolArguments().contains("second"), "最新索引应反映最新请求参数");
    }

    @Test
    void shouldReturnNewestEntriesWhenIndexGrowsLarge() throws Exception {
        Path dir = Files.createTempDirectory("tool-artifacts-large-index-");
        FileToolResultArtifactStore store = new FileToolResultArtifactStore(dir);

        for (int i = 0; i < 200; i++) {
            store.save("mem-large", "search", "{\"q\":\"" + i + "\"}", "result-" + i);
        }

        List<ToolResultArtifactStore.ArtifactRef> recent = store.recent("mem-large", 3);
        assertEquals(3, recent.size());
        assertTrue(recent.get(0).toolArguments().contains("\"199\""));
        assertTrue(recent.get(1).toolArguments().contains("\"198\""));
        assertTrue(recent.get(2).toolArguments().contains("\"197\""));
    }

    @Test
    void shouldPrunePerMemoryIndexWhenConfiguredLimitIsSmall() throws Exception {
        Path dir = Files.createTempDirectory("tool-artifacts-prune-index-");
        FileToolResultArtifactStore store = new FileToolResultArtifactStore(dir, 20);

        for (int i = 0; i < 300; i++) {
            store.save("mem-prune", "search", "{\"q\":\"" + i + "\"}", "result-" + i);
        }

        List<ToolResultArtifactStore.ArtifactRef> recent = store.recent("mem-prune", 100);
        assertEquals(20, recent.size(), "超限后应裁剪到配置上限");
        assertTrue(recent.get(0).toolArguments().contains("\"299\""));
        assertTrue(recent.get(19).toolArguments().contains("\"280\""));
    }
}
