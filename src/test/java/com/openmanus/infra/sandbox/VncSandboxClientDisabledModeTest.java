package com.openmanus.infra.sandbox;

import com.openmanus.infra.config.OpenManusProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VncSandboxClientDisabledModeTest {

    @Test
    void shouldWorkInDisabledModeWithoutDocker() {
        OpenManusProperties properties = new OpenManusProperties();
        properties.getSandbox().setUseSandbox(false);

        VncSandboxClient client = new VncSandboxClient(properties);

        assertFalse(client.isContainerRunning("any-container"));
        assertThrows(IllegalStateException.class, () -> client.createVncSandbox("session-1"));
        client.destroyVncSandbox("any-container");
    }
}
