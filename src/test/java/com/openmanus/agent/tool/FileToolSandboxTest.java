package com.openmanus.agent.tool;

import com.openmanus.domain.service.SessionSandboxManager;
import com.openmanus.infra.sandbox.VncSandboxClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class FileToolSandboxTest {

    @AfterEach
    void cleanupMdc() {
        MDC.remove("sessionId");
    }

    @Test
    void shouldReadAndWriteInsideSessionSandbox() {
        SessionSandboxManager sandboxManager = new SessionSandboxManager(mock(VncSandboxClient.class));
        FileTool fileTool = new FileTool(sandboxManager);
        MDC.put("sessionId", "file-sandbox-" + UUID.randomUUID());

        String writeResult = fileTool.writeFile("notes/todo.txt", "hello sandbox");
        String readResult = fileTool.readFile("notes/todo.txt");

        assertTrue(writeResult.contains("文件写入成功"));
        assertTrue(readResult.contains("hello sandbox"));
    }

    @Test
    void shouldBlockPathTraversalOutsideSandbox() {
        SessionSandboxManager sandboxManager = new SessionSandboxManager(mock(VncSandboxClient.class));
        FileTool fileTool = new FileTool(sandboxManager);
        MDC.put("sessionId", "file-sandbox-" + UUID.randomUUID());

        String result = fileTool.readFile("../outside.txt");
        assertTrue(result.contains("禁止访问沙盒外路径"));
    }

    @Test
    void shouldIsolateFilesBetweenDifferentSessions() {
        SessionSandboxManager sandboxManager = new SessionSandboxManager(mock(VncSandboxClient.class));
        FileTool fileTool = new FileTool(sandboxManager);

        MDC.put("sessionId", "file-sandbox-a-" + UUID.randomUUID());
        String writeResult = fileTool.writeFile("notes/shared.txt", "session-a-only");
        assertTrue(writeResult.contains("文件写入成功"));

        MDC.put("sessionId", "file-sandbox-b-" + UUID.randomUUID());
        String readResult = fileTool.readFile("notes/shared.txt");
        assertTrue(readResult.contains("文件不存在"));
    }

    @Test
    void shouldRejectFileOperationWhenSessionIdMissing() {
        SessionSandboxManager sandboxManager = new SessionSandboxManager(mock(VncSandboxClient.class));
        FileTool fileTool = new FileTool(sandboxManager);
        MDC.remove("sessionId");

        String result = fileTool.readFile("notes/a.txt");
        assertTrue(result.contains("缺少会话ID"));
    }

    @Test
    void shouldRejectBlankPathForWriteAndAppend() {
        SessionSandboxManager sandboxManager = new SessionSandboxManager(mock(VncSandboxClient.class));
        FileTool fileTool = new FileTool(sandboxManager);
        MDC.put("sessionId", "file-sandbox-" + UUID.randomUUID());

        String writeResult = fileTool.writeFile("   ", "abc");
        String appendResult = fileTool.appendFile("", "abc");

        assertTrue(writeResult.contains("文件路径不能为空"));
        assertTrue(appendResult.contains("文件路径不能为空"));
    }

    @Test
    void shouldAllowNullContentAsEmptyStringOnWriteAndAppend() {
        SessionSandboxManager sandboxManager = new SessionSandboxManager(mock(VncSandboxClient.class));
        FileTool fileTool = new FileTool(sandboxManager);
        MDC.put("sessionId", "file-sandbox-" + UUID.randomUUID());

        String writeResult = fileTool.writeFile("notes/null-content.txt", null);
        String appendResult = fileTool.appendFile("notes/null-content.txt", null);
        String readResult = fileTool.readFile("notes/null-content.txt");

        assertTrue(writeResult.contains("文件写入成功"));
        assertTrue(appendResult.contains("内容追加成功"));
        assertTrue(readResult.startsWith("文件内容:"));
    }
}
