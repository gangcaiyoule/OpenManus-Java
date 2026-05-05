package com.openmanus.infra.sandbox;

import com.openmanus.infra.config.OpenManusProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

@DisplayName("SandboxClient Fail Fast Tests")
class SandboxClientFailFastTest {

    @Test
    @DisplayName("constructor should fail fast when Docker image preparation fails")
    void constructor_failsFastWhenDockerUnavailable() {
        OpenManusProperties properties = new OpenManusProperties();
        DockerClientManager dockerClientManager = mock(DockerClientManager.class);
        doThrow(new RuntimeException("docker unavailable"))
                .when(dockerClientManager)
                .pullImageIfNeeded(properties.getSandbox().getImage());

        assertThatThrownBy(() -> new SandboxClient(properties, dockerClientManager))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("docker unavailable");
    }
}
