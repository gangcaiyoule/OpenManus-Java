package com.openmanus.sandbox.domain.port;

import com.openmanus.sandbox.domain.model.SessionSandboxInfo;
import com.openmanus.sandbox.domain.model.SandboxCommandResult;

public interface SandboxRuntimePort {

    SessionSandboxInfo createSandbox(String sessionId);

    SessionSandboxInfo refreshSandboxInfo(String sessionId, SessionSandboxInfo sandboxInfo);

    SandboxCommandResult executeCommand(String sessionId, String command, String cwd, int timeoutSeconds);

    SandboxCommandResult openBrowserUrl(String sessionId, String url);

    SandboxCommandResult executePython(String sessionId, String script, int timeoutSeconds);

    String readTextFile(String sessionId, String path);

    void writeTextFile(String sessionId, String path, String content);

    void destroySandbox(String sessionId);
}
