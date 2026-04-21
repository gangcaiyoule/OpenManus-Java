package com.openmanus.domain.service;

import com.openmanus.domain.model.SessionSandboxInfo;

public interface SessionSandboxClient {

    SessionSandboxInfo createSandbox(String sessionId);

    SessionSandboxInfo refreshSandboxInfo(String sessionId, SessionSandboxInfo sandboxInfo);

    void destroySandbox(String sessionId);
}
