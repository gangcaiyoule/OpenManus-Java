package com.openmanus.smoke.tool;

import com.openmanus.aiframework.runtime.AiCodeExecutionResult;
import com.openmanus.aiframework.runtime.AiCodeSandbox;
import com.openmanus.agent.tool.PythonExecutionTool;
import com.openmanus.smoke.SmokeTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.MDC;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Smoke tests for PythonExecutionTool.
 * Verifies Python code execution functionality works correctly.
 */
@Tag("smoke")
@DisplayName("PythonExecutionTool Smoke Tests")
class PythonExecutionToolSmokeTest implements SmokeTest {

    @TempDir
    Path tempDir;

    private AiCodeSandbox mockSandbox;
    private PythonExecutionTool pythonExecutionTool;

    @BeforeEach
    void setUp() {
        // Set MDC sessionId for SandboxPathResolver
        MDC.put("sessionId", "test-session");
        mockSandbox = mock(AiCodeSandbox.class);
        pythonExecutionTool = createPythonToolWithMockedSandbox(mockSandbox, tempDir);
    }

    private static PythonExecutionTool createPythonToolWithMockedSandbox(AiCodeSandbox sandbox, Path sandboxRoot) {
        var mockGateway = mock(com.openmanus.aiframework.runtime.AiSessionSandboxGateway.class);
        when(mockGateway.resolveWorkspacePath(anyString(), anyString())).thenAnswer(invocation -> {
            String userPath = invocation.getArgument(1, String.class);
            Path resolved = sandboxRoot.resolve(userPath).normalize();
            if (!resolved.startsWith(sandboxRoot)) {
                throw new SecurityException("禁止访问沙盒外路径: " + userPath);
            }
            return resolved.toString();
        });
        var pathResolver = new com.openmanus.sandbox.support.SandboxPathResolver(mockGateway);
        return new PythonExecutionTool(sandbox, pathResolver);
    }

    @Nested
    @DisplayName("executePython")
    class ExecutePythonTests {

        @Test
        @DisplayName("should return success result for valid Python code")
        void executePython_withValidCode_returnsSuccess() {
            // Given
            String code = "print('Hello, World!')";
            when(mockSandbox.executePython(code, 30))
                    .thenReturn(new AiCodeExecutionResult("Hello, World!", "", 0));

            // When
            String result = pythonExecutionTool.executePython("Test", code);

            // Then
            assertThat(result).contains("执行成功");
            assertThat(result).contains("Hello, World!");
        }

        @Test
        @DisplayName("should handle simple arithmetic calculation")
        void executePython_withArithmetic_returnsCorrectResult() {
            // Given
            String code = "result = 2 + 2\nprint(f'Result: {result}')";
            when(mockSandbox.executePython(code, 30))
                    .thenReturn(new AiCodeExecutionResult("Result: 4", "", 0));

            // When
            String result = pythonExecutionTool.executePython("Calculate", code);

            // Then
            assertThat(result).contains("执行成功");
            assertThat(result).contains("Result: 4");
        }

        @Test
        @DisplayName("should return error result for invalid Python code")
        void executePython_withInvalidCode_returnsError() {
            // Given
            String code = "print(undefined_variable)";
            when(mockSandbox.executePython(code, 30))
                    .thenReturn(new AiCodeExecutionResult("", "NameError: name 'undefined_variable' is not defined", 1));

            // When
            String result = pythonExecutionTool.executePython("Test", code);

            // Then
            assertThat(result).contains("执行失败");
            assertThat(result).contains("NameError");
        }

        @Test
        @DisplayName("should handle empty code gracefully")
        void executePython_withEmptyCode_returnsError() {
            // Given
            String code = "";

            // When
            String result = pythonExecutionTool.executePython("Test", code);

            // Then
            assertThat(result).contains("执行失败");
        }

        @Test
        @DisplayName("should include thought in execution log")
        void executePython_withThought_processesThought() {
            // Given
            String thought = "This should calculate 1+1";
            String code = "print(1+1)";
            when(mockSandbox.executePython(code, 30))
                    .thenReturn(new AiCodeExecutionResult("2", "", 0));

            // When
            String result = pythonExecutionTool.executePython(thought, code);

            // Then
            assertThat(result).contains("执行成功");
            verify(mockSandbox).executePython(code, 30);
        }

        @Test
        @DisplayName("should handle stdout with special characters")
        void executePython_withSpecialChars_handlesCorrectly() {
            // Given
            String code = "print('Hello\\nWorld!')";
            when(mockSandbox.executePython(code, 30))
                    .thenReturn(new AiCodeExecutionResult("Hello\nWorld!", "", 0));

            // When
            String result = pythonExecutionTool.executePython("Test", code);

            // Then
            assertThat(result).contains("Hello");
            assertThat(result).contains("World!");
        }
    }

    @Nested
    @DisplayName("executePythonFile")
    class ExecutePythonFileTests {

        @Test
        @DisplayName("should execute Python file successfully")
        void executePythonFile_withValidFile_executesSuccessfully() throws Exception {
            // Given
            Path testFile = tempDir.resolve("test_script.py");
            String source = "print('File execution successful')";
            var gateway = mock(com.openmanus.aiframework.runtime.AiSessionSandboxGateway.class);
            when(gateway.resolveWorkspacePath(anyString(), anyString())).thenReturn(testFile.toString());
            when(gateway.readTextFile(anyString(), eq(testFile.toString()))).thenReturn(source);
            pythonExecutionTool = new PythonExecutionTool(mockSandbox, new com.openmanus.sandbox.support.SandboxPathResolver(gateway));
            when(mockSandbox.executePython(anyString(), anyInt()))
                    .thenReturn(new AiCodeExecutionResult("File execution successful", "", 0));

            // When
            String result = pythonExecutionTool.executePythonFile("test_script.py");

            // Then
            assertThat(result).contains("执行成功");
        }

        @Test
        @DisplayName("should return error for non-existent file")
        void executePythonFile_withNonExistentFile_returnsError() {
            // Given
            String filePath = "nonexistent_file.py";
            var gateway = mock(com.openmanus.aiframework.runtime.AiSessionSandboxGateway.class);
            when(gateway.resolveWorkspacePath(anyString(), anyString())).thenReturn(tempDir.resolve(filePath).toString());
            when(gateway.readTextFile(anyString(), anyString())).thenThrow(new RuntimeException("读取沙箱文件失败: missing"));
            pythonExecutionTool = new PythonExecutionTool(mockSandbox, new com.openmanus.sandbox.support.SandboxPathResolver(gateway));

            // When
            String result = pythonExecutionTool.executePythonFile(filePath);

            // Then
            assertThat(result).contains("文件不存在或不可读");
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should handle sandbox exception")
        void executePython_whenSandboxThrowsException_returnsError() {
            // Given
            String code = "print('test')";
            when(mockSandbox.executePython(code, 30))
                    .thenThrow(new RuntimeException("Sandbox connection failed"));

            // When
            String result = pythonExecutionTool.executePython("Test", code);

            // Then
            assertThat(result).contains("执行失败");
            assertThat(result).contains("Sandbox connection failed");
        }

        @Test
        @DisplayName("should handle timeout by returning error message")
        void executePython_withTimeout_handlesGracefully() {
            // Given
            String code = "import time; time.sleep(60)";
            when(mockSandbox.executePython(code, 30))
                    .thenReturn(new AiCodeExecutionResult("", "Process timed out", 124));

            // When
            String result = pythonExecutionTool.executePython("Test", code);

            // Then
            assertThat(result).contains("执行失败");
        }
    }
}
