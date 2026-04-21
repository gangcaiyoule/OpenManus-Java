package com.openmanus.infra.sandbox;

import com.openmanus.aiframework.runtime.AiCodeExecutionResult;
import com.openmanus.aiframework.runtime.AiVncSandboxInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeSandboxAdaptersTest {

    @Test
    void shouldMapCodeSandboxExecutionResultToRuntimeDto() {
        SandboxClient sandboxClient = mock(SandboxClient.class);
        when(sandboxClient.executePython("print('ok')", 30))
                .thenReturn(new ExecutionResult("ok\n", "", 0));
        RuntimeCodeSandboxAdapter adapter = new RuntimeCodeSandboxAdapter(sandboxClient);

        AiCodeExecutionResult result = adapter.executePython("print('ok')", 30);

        verify(sandboxClient).executePython("print('ok')", 30);
        assertEquals("ok\n", result.stdout());
        assertEquals("", result.stderr());
        assertEquals(0, result.exitCode());
    }

    @Test
    void shouldMapVncSandboxInfoToRuntimeDto() {
        VncSandboxClient client = mock(VncSandboxClient.class);
        when(client.createVncSandbox("s1"))
                .thenReturn(new VncSandboxInfo("cid", "http://127.0.0.1:8080/vnc.html", 8080));
        RuntimeVncSandboxClientAdapter adapter = new RuntimeVncSandboxClientAdapter(client);

        AiVncSandboxInfo info = adapter.createVncSandbox("s1");

        assertEquals("cid", info.containerId());
        assertEquals("http://127.0.0.1:8080/vnc.html", info.vncUrl());
        assertEquals(8080, info.mappedPort());
        adapter.destroyVncSandbox("cid");
        verify(client).destroyVncSandbox("cid");
    }
}
