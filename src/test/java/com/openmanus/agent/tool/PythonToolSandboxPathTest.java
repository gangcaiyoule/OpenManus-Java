package com.openmanus.agent.tool;

import com.openmanus.aiframework.runtime.AiCodeExecutionResult;
import com.openmanus.aiframework.runtime.AiCodeSandbox;
import com.openmanus.aiframework.runtime.AiSessionSandboxGateway;
import com.openmanus.aiframework.runtime.AiSessionSandboxInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PythonToolSandboxPathTest {

    @AfterEach
    void cleanupMdc() {
        MDC.remove("sessionId");
    }

    @Test
    void shouldReadPythonFileFromSessionSandbox() throws Exception {
        TestSessionSandboxGateway sandboxGateway = new TestSessionSandboxGateway();
        AiCodeSandbox sandbox = mock(AiCodeSandbox.class);
        PythonTool pythonTool = new PythonTool(sandbox, sandboxGateway);

        MDC.put("sessionId", "python-sandbox-1");
        Path root = sandboxGateway.getOrCreateFileSandboxRoot("python-sandbox-1");
        Path script = root.resolve("scripts/hello.py");
        Files.createDirectories(script.getParent());
        Files.writeString(script, "print('ok')");

        when(sandbox.executePython(eq("print('ok')"), anyInt()))
                .thenReturn(new AiCodeExecutionResult("ok", "", 0));

        String result = pythonTool.executePythonFile("scripts/hello.py");

        verify(sandbox).executePython(eq("print('ok')"), anyInt());
        assertTrue(result.contains("执行成功"));
    }

    @Test
    void shouldBlockPathTraversalOutsideSandbox() {
        AiCodeSandbox sandbox = mock(AiCodeSandbox.class);
        PythonTool pythonTool = new PythonTool(sandbox, new TestSessionSandboxGateway());

        MDC.put("sessionId", "python-sandbox-2");
        String result = pythonTool.executePythonFile("../outside.py");

        verify(sandbox, never()).executePython(anyString(), anyInt());
        assertTrue(result.contains("禁止访问沙盒外路径"));
    }

    @Test
    void shouldRejectPythonFileExecutionWhenSessionIdMissing() {
        AiCodeSandbox sandbox = mock(AiCodeSandbox.class);
        PythonTool pythonTool = new PythonTool(sandbox, new TestSessionSandboxGateway());
        MDC.remove("sessionId");

        String result = pythonTool.executePythonFile("scripts/job.py");

        verify(sandbox, never()).executePython(anyString(), anyInt());
        assertTrue(result.contains("缺少会话ID"));
    }

    @Test
    void shouldRejectBlankPythonCodeInput() {
        AiCodeSandbox sandbox = mock(AiCodeSandbox.class);
        PythonTool pythonTool = new PythonTool(sandbox, new TestSessionSandboxGateway());

        String result = pythonTool.executePython("plan", "   ");

        verify(sandbox, never()).executePython(anyString(), anyInt());
        assertTrue(result.contains("Python代码不能为空"));
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
