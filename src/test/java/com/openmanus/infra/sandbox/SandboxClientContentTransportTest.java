package com.openmanus.infra.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CopyArchiveToContainerCmd;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectExecCmd;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.openmanus.infra.config.OpenManusProperties;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SandboxClient content transport tests")
class SandboxClientContentTransportTest {

    @Test
    @DisplayName("executePython should stream large script through stdin")
    void executePython_streamsScriptThroughStdin() throws Exception {
        DockerClient dockerClient = mock(DockerClient.class);
        DockerClientManager dockerManager = mock(DockerClientManager.class);
        when(dockerManager.getClient()).thenReturn(dockerClient);
        when(dockerManager.isContainerRunning("container-1")).thenReturn(true);

        SandboxClient sandboxClient = new SandboxClient(new OpenManusProperties(), dockerManager);
        registerContainer(sandboxClient, "session-1", "container-1");

        ExecCreateCmd execCreateCmd = mock(ExecCreateCmd.class, RETURNS_SELF);
        ExecCreateCmdResponse execCreateResponse = mock(ExecCreateCmdResponse.class);
        when(execCreateResponse.getId()).thenReturn("exec-1");
        when(execCreateCmd.exec()).thenReturn(execCreateResponse);
        when(dockerClient.execCreateCmd("container-1")).thenReturn(execCreateCmd);

        ExecStartCmd execStartCmd = mock(ExecStartCmd.class, RETURNS_SELF);
        when(dockerClient.execStartCmd("exec-1")).thenReturn(execStartCmd);
        when(execStartCmd.exec(any(ExecStartResultCallback.class))).thenAnswer(invocation -> {
            ExecStartResultCallback callback = invocation.getArgument(0);
            callback.onComplete();
            return callback;
        });

        InspectExecCmd inspectExecCmd = mock(InspectExecCmd.class);
        InspectExecResponse inspectExecResponse = mock(InspectExecResponse.class);
        when(inspectExecResponse.getExitCodeLong()).thenReturn(0L);
        when(inspectExecCmd.exec()).thenReturn(inspectExecResponse);
        when(dockerClient.inspectExecCmd("exec-1")).thenReturn(inspectExecCmd);

        String script = "print('x')\n".repeat(50000);
        ExecutionResult result = sandboxClient.executePython("session-1", script, 1);

        assertThat(result.isSuccess()).isTrue();
        verify(execCreateCmd).withCmd("/bin/sh", "-lc", "python3 -");
        ArgumentCaptor<InputStream> stdinCaptor = ArgumentCaptor.forClass(InputStream.class);
        verify(execStartCmd).withStdIn(stdinCaptor.capture());
        assertThat(readAll(stdinCaptor.getValue())).isEqualTo(script);
    }

    @Test
    @DisplayName("writeTextFile should copy tar archive into container instead of inlining content")
    void writeTextFile_copiesArchiveIntoContainer() throws Exception {
        DockerClient dockerClient = mock(DockerClient.class);
        DockerClientManager dockerManager = mock(DockerClientManager.class);
        when(dockerManager.getClient()).thenReturn(dockerClient);
        when(dockerManager.isContainerRunning("container-1")).thenReturn(true);

        SandboxClient sandboxClient = new SandboxClient(new OpenManusProperties(), dockerManager);
        registerContainer(sandboxClient, "session-1", "container-1");

        ExecCreateCmd execCreateCmd = mock(ExecCreateCmd.class, RETURNS_SELF);
        ExecCreateCmdResponse execCreateResponse = mock(ExecCreateCmdResponse.class);
        when(execCreateResponse.getId()).thenReturn("exec-mkdir");
        when(execCreateCmd.exec()).thenReturn(execCreateResponse);
        when(dockerClient.execCreateCmd("container-1")).thenReturn(execCreateCmd);

        ExecStartCmd execStartCmd = mock(ExecStartCmd.class, RETURNS_SELF);
        when(dockerClient.execStartCmd("exec-mkdir")).thenReturn(execStartCmd);
        when(execStartCmd.exec(any(ExecStartResultCallback.class))).thenAnswer(invocation -> {
            ExecStartResultCallback callback = invocation.getArgument(0);
            callback.onComplete();
            return callback;
        });

        InspectExecCmd inspectExecCmd = mock(InspectExecCmd.class);
        InspectExecResponse inspectExecResponse = mock(InspectExecResponse.class);
        when(inspectExecResponse.getExitCodeLong()).thenReturn(0L);
        when(inspectExecCmd.exec()).thenReturn(inspectExecResponse);
        when(dockerClient.inspectExecCmd("exec-mkdir")).thenReturn(inspectExecCmd);

        CopyArchiveToContainerCmd copyCmd = mock(CopyArchiveToContainerCmd.class, RETURNS_SELF);
        when(dockerClient.copyArchiveToContainerCmd("container-1")).thenReturn(copyCmd);

        String content = "large-result-line\n" + "x".repeat(220000);
        sandboxClient.writeTextFile("session-1", "/workspace/data/result.txt", content);

        verify(execCreateCmd).withCmd("/bin/sh", "-lc", "mkdir -p '/workspace/data'");
        verify(copyCmd).withRemotePath("/workspace/data");
        ArgumentCaptor<InputStream> tarCaptor = ArgumentCaptor.forClass(InputStream.class);
        verify(copyCmd).withTarInputStream(tarCaptor.capture());
        assertTarContainsSingleFile(tarCaptor.getValue(), "result.txt", content);
    }

    @SuppressWarnings("unchecked")
    private static void registerContainer(SandboxClient sandboxClient, String sessionId, String containerId) throws Exception {
        Field field = SandboxClient.class.getDeclaredField("sessionContainers");
        field.setAccessible(true);
        Map<String, SandboxClient.SessionContainer> containers =
                (Map<String, SandboxClient.SessionContainer>) field.get(sandboxClient);
        containers.put(sessionId, new SandboxClient.SessionContainer(sessionId, containerId, "/workspace"));
    }

    private static String readAll(InputStream inputStream) throws Exception {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void assertTarContainsSingleFile(InputStream tarStream, String fileName, String content) throws Exception {
        try (TarArchiveInputStream tar = new TarArchiveInputStream(tarStream)) {
            var entry = tar.getNextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).isEqualTo(fileName);
            assertThat(new String(tar.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(content);
            assertThat(tar.getNextEntry()).isNull();
        }
    }
}
