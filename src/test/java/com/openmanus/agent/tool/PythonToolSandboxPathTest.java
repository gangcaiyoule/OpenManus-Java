package com.openmanus.agent.tool;

import com.openmanus.domain.service.SessionSandboxManager;
import com.openmanus.infra.sandbox.ExecutionResult;
import com.openmanus.infra.sandbox.SandboxClient;
import com.openmanus.infra.sandbox.VncSandboxClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.nio.file.Files;
import java.nio.file.Path;

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
        SessionSandboxManager sandboxManager = new SessionSandboxManager(mock(VncSandboxClient.class));
        SandboxClient sandboxClient = mock(SandboxClient.class);
        PythonTool pythonTool = new PythonTool(sandboxClient, sandboxManager);

        MDC.put("sessionId", "python-sandbox-1");
        Path root = sandboxManager.getOrCreateFileSandboxRoot("python-sandbox-1");
        Path script = root.resolve("scripts/hello.py");
        Files.createDirectories(script.getParent());
        Files.writeString(script, "print('ok')");

        when(sandboxClient.executePython(eq("print('ok')"), anyInt()))
                .thenReturn(new ExecutionResult("ok", "", 0));

        String result = pythonTool.executePythonFile("scripts/hello.py");

        verify(sandboxClient).executePython(eq("print('ok')"), anyInt());
        assertTrue(result.contains("执行成功"));
    }

    @Test
    void shouldBlockPathTraversalOutsideSandbox() {
        SessionSandboxManager sandboxManager = new SessionSandboxManager(mock(VncSandboxClient.class));
        SandboxClient sandboxClient = mock(SandboxClient.class);
        PythonTool pythonTool = new PythonTool(sandboxClient, sandboxManager);

        MDC.put("sessionId", "python-sandbox-2");
        String result = pythonTool.executePythonFile("../outside.py");

        verify(sandboxClient, never()).executePython(anyString(), anyInt());
        assertTrue(result.contains("禁止访问沙盒外路径"));
    }

    @Test
    void shouldRejectPythonFileExecutionWhenSessionIdMissing() {
        SessionSandboxManager sandboxManager = new SessionSandboxManager(mock(VncSandboxClient.class));
        SandboxClient sandboxClient = mock(SandboxClient.class);
        PythonTool pythonTool = new PythonTool(sandboxClient, sandboxManager);
        MDC.remove("sessionId");

        String result = pythonTool.executePythonFile("scripts/job.py");

        verify(sandboxClient, never()).executePython(anyString(), anyInt());
        assertTrue(result.contains("缺少会话ID"));
    }

    @Test
    void shouldRejectBlankPythonCodeInput() {
        SessionSandboxManager sandboxManager = new SessionSandboxManager(mock(VncSandboxClient.class));
        SandboxClient sandboxClient = mock(SandboxClient.class);
        PythonTool pythonTool = new PythonTool(sandboxClient, sandboxManager);

        String result = pythonTool.executePython("plan", "   ");

        verify(sandboxClient, never()).executePython(anyString(), anyInt());
        assertTrue(result.contains("Python代码不能为空"));
    }
}
