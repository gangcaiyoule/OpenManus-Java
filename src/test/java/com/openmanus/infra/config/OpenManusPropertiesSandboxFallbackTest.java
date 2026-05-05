package com.openmanus.infra.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OpenManusProperties Sandbox Fallback Tests")
class OpenManusPropertiesSandboxFallbackTest {

    private static final List<String> SANDBOX_PROPERTY_KEYS = List.of(
            "OPENMANUS_APP_DEFAULT_USER_ID",
            "OPENMANUS_APP_DEFAULTUSERID",
            "USER_ID",
            "OPENMANUS_SANDBOX_IMAGE",
            "OPENMANUS_SANDBOX_WORK_DIR",
            "OPENMANUS_SANDBOX_MEMORY_LIMIT",
            "OPENMANUS_SANDBOX_CPU_LIMIT",
            "OPENMANUS_SANDBOX_TIMEOUT",
            "OPENMANUS_SANDBOX_NETWORK_ENABLED"
    );

    @AfterEach
    void clearSandboxProperties() {
        SANDBOX_PROPERTY_KEYS.forEach(System::clearProperty);
    }

    @Test
    @DisplayName("applyEnvFallbacks should populate default user id from system properties")
    void applyEnvFallbacks_populatesDefaultUserIdFromSystemProperties() {
        System.setProperty("USER_ID", "user-009");

        OpenManusProperties properties = new OpenManusProperties();

        properties.applyEnvFallbacks();

        assertThat(properties.getApp().getDefaultUserId()).isEqualTo("user-009");
    }

    @Test
    @DisplayName("applyEnvFallbacks should populate sandbox settings from system properties")
    void applyEnvFallbacks_populatesSandboxSettingsFromSystemProperties() {
        System.setProperty("OPENMANUS_SANDBOX_IMAGE", "manus/sandbox:latest");
        System.setProperty("OPENMANUS_SANDBOX_WORK_DIR", "/tmp/manus-workspace");
        System.setProperty("OPENMANUS_SANDBOX_MEMORY_LIMIT", "2g");
        System.setProperty("OPENMANUS_SANDBOX_CPU_LIMIT", "1.5");
        System.setProperty("OPENMANUS_SANDBOX_TIMEOUT", "45");
        System.setProperty("OPENMANUS_SANDBOX_NETWORK_ENABLED", "true");

        OpenManusProperties properties = new OpenManusProperties();

        properties.applyEnvFallbacks();

        assertThat(properties.getSandbox().getImage()).isEqualTo("manus/sandbox:latest");
        assertThat(properties.getSandbox().getWorkDir()).isEqualTo("/tmp/manus-workspace");
        assertThat(properties.getSandbox().getMemoryLimit()).isEqualTo("2g");
        assertThat(properties.getSandbox().getCpuLimit()).isEqualTo(1.5d);
        assertThat(properties.getSandbox().getTimeout()).isEqualTo(45);
        assertThat(properties.getSandbox().isNetworkEnabled()).isTrue();
    }

    @Test
    @DisplayName("applyEnvFallbacks should keep sandbox defaults when fallback values are invalid")
    void applyEnvFallbacks_keepsDefaultsWhenValuesAreInvalid() {
        OpenManusProperties properties = new OpenManusProperties();
        double defaultCpuLimit = properties.getSandbox().getCpuLimit();
        int defaultTimeout = properties.getSandbox().getTimeout();
        boolean defaultNetworkEnabled = properties.getSandbox().isNetworkEnabled();

        System.setProperty("OPENMANUS_SANDBOX_CPU_LIMIT", "-2");
        System.setProperty("OPENMANUS_SANDBOX_TIMEOUT", "0");
        System.setProperty("OPENMANUS_SANDBOX_NETWORK_ENABLED", "maybe");

        properties.applyEnvFallbacks();

        assertThat(properties.getSandbox().getCpuLimit()).isEqualTo(defaultCpuLimit);
        assertThat(properties.getSandbox().getTimeout()).isEqualTo(defaultTimeout);
        assertThat(properties.getSandbox().isNetworkEnabled()).isEqualTo(defaultNetworkEnabled);
    }
}
