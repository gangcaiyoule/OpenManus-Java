package com.openmanus.agent.tool;

import com.openmanus.aiframework.runtime.AiSessionSandboxGateway;
import com.openmanus.aiframework.runtime.AiSessionSandboxInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FileToolSandboxTest {

    @AfterEach
    void cleanupMdc() {
        MDC.remove("sessionId");
    }

    @Test
    void shouldReadAndWriteInsideSessionSandbox() {
        FileTool fileTool = new FileTool(new TestSessionSandboxGateway());
        MDC.put("sessionId", "file-sandbox-" + UUID.randomUUID());

        String writeResult = fileTool.writeFile("notes/todo.txt", "hello sandbox");
        String readResult = fileTool.readFile("notes/todo.txt");

        assertTrue(writeResult.contains("文件写入成功"));
        assertTrue(readResult.contains("hello sandbox"));
    }

    @Test
    void shouldBlockPathTraversalOutsideSandbox() {
        FileTool fileTool = new FileTool(new TestSessionSandboxGateway());
        MDC.put("sessionId", "file-sandbox-" + UUID.randomUUID());

        String result = fileTool.readFile("../outside.txt");
        assertTrue(result.contains("禁止访问沙盒外路径"));
    }

    @Test
    void shouldIsolateFilesBetweenDifferentSessions() {
        FileTool fileTool = new FileTool(new TestSessionSandboxGateway());

        MDC.put("sessionId", "file-sandbox-a-" + UUID.randomUUID());
        String writeResult = fileTool.writeFile("notes/shared.txt", "session-a-only");
        assertTrue(writeResult.contains("文件写入成功"));

        MDC.put("sessionId", "file-sandbox-b-" + UUID.randomUUID());
        String readResult = fileTool.readFile("notes/shared.txt");
        assertTrue(readResult.contains("文件不存在"));
    }

    @Test
    void shouldRejectFileOperationWhenSessionIdMissing() {
        FileTool fileTool = new FileTool(new TestSessionSandboxGateway());
        MDC.remove("sessionId");

        String result = fileTool.readFile("notes/a.txt");
        assertTrue(result.contains("缺少会话ID"));
    }

    @Test
    void shouldRejectBlankPathForWriteAndAppend() {
        FileTool fileTool = new FileTool(new TestSessionSandboxGateway());
        MDC.put("sessionId", "file-sandbox-" + UUID.randomUUID());

        String writeResult = fileTool.writeFile("   ", "abc");
        String appendResult = fileTool.appendFile("", "abc");

        assertTrue(writeResult.contains("文件路径不能为空"));
        assertTrue(appendResult.contains("文件路径不能为空"));
    }

    @Test
    void shouldAllowNullContentAsEmptyStringOnWriteAndAppend() {
        FileTool fileTool = new FileTool(new TestSessionSandboxGateway());
        MDC.put("sessionId", "file-sandbox-" + UUID.randomUUID());

        String writeResult = fileTool.writeFile("notes/null-content.txt", null);
        String appendResult = fileTool.appendFile("notes/null-content.txt", null);
        String readResult = fileTool.readFile("notes/null-content.txt");

        assertTrue(writeResult.contains("文件写入成功"));
        assertTrue(appendResult.contains("内容追加成功"));
        assertTrue(readResult.startsWith("文件内容:"));
    }

    private static final class TestSessionSandboxGateway implements AiSessionSandboxGateway {
        private static final Path ROOT = Path.of(System.getProperty("java.io.tmpdir"),
                "openmanus", "agent-tool-tests");

        @Override
        public Optional<AiSessionSandboxInfo> getSandboxInfo(String sessionId) {
            return Optional.empty();
        }

        @Override
        public AiSessionSandboxInfo getOrCreateSandbox(String sessionId) {
            return new AiSessionSandboxInfo(sessionId, "container", "http://localhost", 0, "RUNNING");
        }

        @Override
        public Path getOrCreateFileSandboxRoot(String sessionId) {
            if (sessionId == null || sessionId.isBlank()) {
                throw new SecurityException("缺少会话ID，拒绝创建文件沙盒目录");
            }
            Path root = ROOT.resolve(sessionId).toAbsolutePath().normalize();
            try {
                Files.createDirectories(root);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return root;
        }
    }
}
