package com.openmanus.smoke.agent;

import com.openmanus.aiframework.runtime.AiChatModel;
import com.openmanus.aiframework.runtime.AiMemory;
import com.openmanus.aiframework.runtime.AiMemoryProvider;
import com.openmanus.aiframework.runtime.model.AiChatMessage;
import com.openmanus.aiframework.runtime.model.AiChatRequest;
import com.openmanus.aiframework.runtime.model.AiChatResponse;
import com.openmanus.aiframework.runtime.model.AiFinishReason;
import com.openmanus.aiframework.runtime.model.AiToolCall;
import com.openmanus.aiframework.runtime.model.AiTokenUsage;
import com.openmanus.agent.coordination.AgentCoordinator;
import com.openmanus.domain.model.AgentExecutionEvent;
import com.openmanus.domain.service.ExecutionEventPort;
import com.openmanus.smoke.SmokeTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Smoke tests for AgentCoordinator.
 * Verifies the core ReAct loop functionality works correctly.
 */
@Tag("smoke")
@DisplayName("AgentCoordinator Smoke Tests")
class AgentCoordinatorSmokeTest implements SmokeTest {

    private MockAiChatModel mockChatModel;
    private MockAiMemory mockMemory;
    private AgentCoordinator agent;

    @BeforeEach
    void setUp() {
        // Set MDC sessionId for SandboxPathResolver
        MDC.put("sessionId", "test-session");
        mockChatModel = new MockAiChatModel();
        mockMemory = new MockAiMemory("test-session");
    }

    @Nested
    @DisplayName("execute")
    class ExecuteTests {

        @Test
        @DisplayName("should return response when model provides direct answer")
        void execute_withDirectAnswer_returnsResponse() {
            // Given
            mockChatModel.addResponse(
                    AiChatMessage.assistant("This is a direct answer from the model.")
            );
            agent = buildAgent();

            // When
            String result = agent.execute("Hello, how are you?", "test-session");

            // Then
            assertThat(result).isNotBlank();
            assertThat(result).contains("direct answer");
            assertThat(mockChatModel.getCallCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should handle multi-turn conversation")
        void execute_withMultiTurnConversation_maintainsContext() {
            // Given
            mockChatModel.addResponse(AiChatMessage.assistant("First response"));
            mockChatModel.addResponse(AiChatMessage.assistant("Second response"));
            agent = buildAgent();

            // When
            String firstResult = agent.execute("First message", "test-session");
            String secondResult = agent.execute("Second message", "test-session");

            // Then
            assertThat(firstResult).isNotBlank();
            assertThat(secondResult).isNotBlank();
            assertThat(mockChatModel.getCallCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("should throw exception for null input")
        void execute_withNullInput_throwsException() {
            // Given
            agent = buildAgent();

            // When/Then
            assertThatThrownBy(() -> agent.execute(null, "test-session"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be null or blank");
        }

        @Test
        @DisplayName("should throw exception for blank input")
        void execute_withBlankInput_throwsException() {
            // Given
            agent = buildAgent();

            // When/Then
            assertThatThrownBy(() -> agent.execute("   ", "test-session"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should throw exception when model returns null response")
        void execute_withNullModelResponse_throwsException() {
            // Given
            mockChatModel.setResponseToReturn(null);
            agent = buildAgent();

            // When/Then
            assertThatThrownBy(() -> agent.execute("Test message", "test-session"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("failed to generate");
        }

        @Test
        @DisplayName("should return empty string when assistant message has no content and no tool calls")
        void execute_withEmptyAssistantMessage_returnsEmpty() {
            // Given
            AiChatMessage emptyMessage = new AiChatMessage(
                    AiChatMessage.Role.ASSISTANT,
                    "",
                    null,
                    null,
                    List.of()
            );
            mockChatModel.addResponse(emptyMessage);
            agent = buildAgent();

            // When
            String result = agent.execute("Test", "test-session");

            // Then - returns empty string when message has no content and no tool calls
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Iteration Limits")
    class IterationLimitTests {

        @Test
        @DisplayName("should throw exception when max iterations exceeded")
        void execute_withMaxIterationsExceeded_throwsException() {
            // Given - always return tool calls to force iteration
            AiToolCall toolCall = new AiToolCall("call_1", "unknownTool", "{}");
            mockChatModel.addResponse(AiChatMessage.assistant("Calling tool", List.of(toolCall)));
            mockChatModel.addResponse(AiChatMessage.assistant("Calling tool again", List.of(toolCall)));
            mockChatModel.addResponse(AiChatMessage.assistant("Still going", List.of(toolCall)));

            agent = AgentCoordinator.builder()
                    .aiChatModel(mockChatModel)
                    .aiMemoryProvider(sessionId -> mockMemory)
                    .maxIterations(2) // Low limit to trigger exception
                    .build();

            // When/Then
            assertThatThrownBy(() -> agent.execute("Keep calling tools", "test-session"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("maximum iterations");
        }

        @Test
        @DisplayName("should complete successfully within iteration limit")
        void execute_withToolCallsExhaustedCompletes() {
            // Given - one tool call then final answer
            mockChatModel.addResponse(AiChatMessage.assistant("Calling tool", List.of(
                    new AiToolCall("call_1", "unknownTool", "{}")
            )));
            mockChatModel.addResponse(AiChatMessage.assistant("Final answer: 1"));

            agent = AgentCoordinator.builder()
                    .aiChatModel(mockChatModel)
                    .aiMemoryProvider(sessionId -> mockMemory)
                    .maxIterations(10) // High limit
                    .build();

            // When
            String result = agent.execute("Calculate", "test-session");

            // Then
            assertThat(result).isNotBlank();
            assertThat(result).contains("Final answer");
            assertThat(mockChatModel.getCallCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Memory Integration")
    class MemoryIntegrationTests {

        @Test
        @DisplayName("should persist messages to memory")
        void execute_withMemoryEnabled_persistsMessages() {
            // Given
            mockChatModel.addResponse(AiChatMessage.assistant("Response with memory."));
            agent = buildAgent();

            // When
            agent.execute("Test message", "test-session");

            // Then
            assertThat(mockMemory.getMessageCount()).isGreaterThan(0);
        }

        @Test
        @DisplayName("should use default memory when memory provider returns null")
        void execute_withNullMemoryProvider_continuesWithoutMemory() {
            // Given
            mockChatModel.addResponse(AiChatMessage.assistant("Response without memory."));

            agent = AgentCoordinator.builder()
                    .aiChatModel(mockChatModel)
                    .maxIterations(3)
                    .build();

            // When
            String result = agent.execute("Test", "test-session");

            // Then
            assertThat(result).isNotBlank();
        }

        @Test
        @DisplayName("should add system message to memory")
        void execute_addsSystemMessageToMemory() {
            // Given
            mockChatModel.addResponse(AiChatMessage.assistant("Response"));
            agent = buildAgent();

            // When
            agent.execute("Test", "test-session");

            // Then
            assertThat(mockMemory.getMessages())
                    .anyMatch(msg -> msg.role() == AiChatMessage.Role.SYSTEM);
        }

        @Test
        @DisplayName("should keep full memory history in model request")
        void execute_withMemoryHistory_keepsFullHistory() {
            mockMemory.add(AiChatMessage.system("system"));
            mockMemory.add(AiChatMessage.user("older request"));
            mockMemory.add(AiChatMessage.assistant("older answer"));
            mockChatModel.addResponse(AiChatMessage.assistant("final answer"));
            agent = buildAgent();

            String result = agent.execute("current request", "test-session");

            assertThat(result).contains("final answer");
            assertThat(mockChatModel.getRequests()).hasSize(1);
            assertThat(mockChatModel.getRequests().getFirst().messages())
                    .extracting(AiChatMessage::content)
                    .containsSequence("system", "older request", "older answer", "current request");
        }
    }

    @Nested
    @DisplayName("Protocol Validation")
    class ProtocolValidationTests {

        @Test
        @DisplayName("should allow a closed single-tool result block")
        void execute_withClosedSingleToolResultBlock_allowsProviderRequest() {
            mockMemory.add(assistantToolCalls("call-1"));
            mockMemory.add(toolResult("call-1"));
            mockChatModel.addResponse(AiChatMessage.assistant("final answer"));
            agent = buildAgent();

            String result = agent.execute("current request", "test-session");

            assertThat(result).contains("final answer");
            assertThat(mockChatModel.getCallCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should allow a closed multi-tool result block")
        void execute_withClosedMultiToolResultBlock_allowsProviderRequest() {
            mockMemory.add(assistantToolCalls("call-a", "call-b"));
            mockMemory.add(toolResult("call-a"));
            mockMemory.add(toolResult("call-b"));
            mockChatModel.addResponse(AiChatMessage.assistant("final answer"));
            agent = buildAgent();

            String result = agent.execute("current request", "test-session");

            assertThat(result).contains("final answer");
            assertThat(mockChatModel.getCallCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should fail fast when tool message is missing toolCallId")
        void execute_withToolMessageMissingToolCallId_failsFast() {
            mockMemory.add(new AiChatMessage(
                    AiChatMessage.Role.TOOL,
                    "tool output",
                    "fileTool",
                    null,
                    List.of()
            ));
            agent = buildAgent();

            assertThatThrownBy(() -> agent.execute("current request", "test-session"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("missing toolCallId");
            assertThat(mockChatModel.getCallCount()).isZero();
        }

        @Test
        @DisplayName("should fail fast when tool message is orphaned")
        void execute_withOrphanToolMessage_failsFast() {
            mockMemory.add(AiChatMessage.assistant("older answer"));
            mockMemory.add(new AiChatMessage(
                    AiChatMessage.Role.TOOL,
                    "tool output",
                    "fileTool",
                    "call-1",
                    List.of()
            ));
            agent = buildAgent();

            assertThatThrownBy(() -> agent.execute("current request", "test-session"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("has no open assistant tool-call block");
            assertThat(mockChatModel.getCallCount()).isZero();
        }

        @Test
        @DisplayName("should fail fast when multi-tool result block is incomplete")
        void execute_withIncompleteMultiToolResultBlock_failsFast() {
            mockMemory.add(assistantToolCalls("call-a", "call-b"));
            mockMemory.add(toolResult("call-a"));
            agent = buildAgent();

            assertThatThrownBy(() -> agent.execute("current request", "test-session"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("tool-call block at index")
                    .hasMessageContaining("is incomplete");
            assertThat(mockChatModel.getCallCount()).isZero();
        }

        @Test
        @DisplayName("should fail fast when toolCallId does not match current assistant block")
        void execute_withToolMessageOutsideCurrentAssistantBlock_failsFast() {
            mockMemory.add(assistantToolCalls("call-a"));
            mockMemory.add(toolResult("call-b"));
            agent = buildAgent();

            assertThatThrownBy(() -> agent.execute("current request", "test-session"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("does not match current assistant tool-call block");
            assertThat(mockChatModel.getCallCount()).isZero();
        }
    }

    @Nested
    @DisplayName("Tool Invocation")
    class ToolInvocationTests {

        @Test
        @DisplayName("should handle unknown tool gracefully")
        void execute_withUnknownTool_handlesGracefully() {
            // Given
            AiToolCall unknownToolCall = new AiToolCall(
                    "call_unknown",
                    "nonExistentTool",
                    "{}"
            );
            mockChatModel.addResponse(AiChatMessage.assistant("Trying to call unknown tool.", List.of(unknownToolCall)));
            mockChatModel.addResponse(AiChatMessage.assistant("I couldn't complete the task."));
            agent = buildAgent();

            // When
            String result = agent.execute("Use non-existent tool", "test-session");

            // Then
            assertThat(result).isNotBlank();
        }

        @Test
        @DisplayName("should include tool result in loop continuation")
        void execute_withUnknownTool_includesErrorInContext() {
            // Given
            AiToolCall toolCall = new AiToolCall("call_1", "unknownTool", "{}");
            mockChatModel.addResponse(AiChatMessage.assistant("Calling tool", List.of(toolCall)));
            mockChatModel.addResponse(AiChatMessage.assistant("Got the error response."));

            agent = AgentCoordinator.builder()
                    .aiChatModel(mockChatModel)
                    .aiMemoryProvider(sessionId -> mockMemory)
                    .maxIterations(5)
                    .build();

            // When
            String result = agent.execute("Test", "test-session");

            // Then
            assertThat(result).isNotBlank();
            assertThat(result).contains("Got the error response");
            assertThat(mockChatModel.getCallCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("should publish model and tool events for frontend")
        void execute_withToolCall_publishesFrontendEvents() {
            // Given
            CapturingExecutionEventPort eventPort = new CapturingExecutionEventPort();
            AiToolCall toolCall = new AiToolCall("call_1", "unknownTool", "{\"query\":\"openai\"}");
            mockChatModel.addResponse(AiChatMessage.assistant("Calling tool", List.of(toolCall)));
            mockChatModel.addResponse(AiChatMessage.assistant("Done"));

            agent = AgentCoordinator.builder()
                    .aiChatModel(mockChatModel)
                    .aiMemoryProvider(sessionId -> mockMemory)
                    .executionEventPort(eventPort)
                    .maxIterations(5)
                    .build();

            // When
            String result = agent.execute("Test", "test-session");

            // Then
            assertThat(result).isEqualTo("Done");
            assertThat(eventPort.events)
                    .extracting(AgentExecutionEvent::getEventType)
                    .contains(
                            AgentExecutionEvent.EventType.LLM_REQUEST,
                            AgentExecutionEvent.EventType.LLM_RESPONSE,
                            AgentExecutionEvent.EventType.TOOL_CALL_START,
                            AgentExecutionEvent.EventType.TOOL_CALL_END
                    );
            AgentExecutionEvent toolStart = eventPort.events.stream()
                    .filter(event -> event.getEventType() == AgentExecutionEvent.EventType.TOOL_CALL_START)
                    .findFirst()
                    .orElseThrow();
            assertThat(toolStart.getInput()).isEqualTo("{\"query\":\"openai\"}");
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should handle empty tool calls list")
        void execute_withEmptyToolCalls_returnsContent() {
            // Given
            mockChatModel.addResponse(AiChatMessage.assistant("Here is a response", List.of()));
            agent = buildAgent();

            // When
            String result = agent.execute("Test", "test-session");

            // Then
            assertThat(result).contains("Here is a response");
        }

        @Test
        @DisplayName("should handle null tool calls in assistant message")
        void execute_withNullToolCalls_returnsContent() {
            // Given
            AiChatMessage messageWithNullToolCalls = new AiChatMessage(
                    AiChatMessage.Role.ASSISTANT,
                    "Response without tools",
                    null,
                    null,
                    null
            );
            mockChatModel.addResponse(messageWithNullToolCalls);
            agent = buildAgent();

            // When
            String result = agent.execute("Test", "test-session");

            // Then
            assertThat(result).contains("Response without tools");
        }

        @Test
        @DisplayName("should return empty string when assistant message has empty content and no tool calls")
        void execute_withAssistantMessageMissingContentAndToolCalls_returnsEmpty() {
            // Given - assistant message with empty content and no tool calls
            AiChatMessage emptyMessage = new AiChatMessage(
                    AiChatMessage.Role.ASSISTANT,
                    "",
                    null,
                    null,
                    List.of()
            );
            mockChatModel.addResponse(emptyMessage);
            agent = buildAgent();

            // When
            String result = agent.execute("Test", "test-session");

            // Then - returns empty string when message has no content and no tool calls
            // The agent treats empty assistant messages as final answers
            assertThat(result).isEmpty();
        }
    }

    // Helper methods

    private AgentCoordinator buildAgent() {
        return AgentCoordinator.builder()
                .aiChatModel(mockChatModel)
                .aiMemoryProvider(sessionId -> mockMemory)
                .maxIterations(3)
                .build();
    }

    private static AiChatMessage assistantToolCalls(String... ids) {
        List<AiToolCall> toolCalls = new ArrayList<>();
        for (String id : ids) {
            toolCalls.add(new AiToolCall(id, "mockTool" + id, "{}"));
        }
        return AiChatMessage.assistant("calling tools", toolCalls);
    }

    private static AiChatMessage toolResult(String toolCallId) {
        return new AiChatMessage(
                AiChatMessage.Role.TOOL,
                "tool output for " + toolCallId,
                "mockTool",
                toolCallId,
                List.of()
        );
    }

    // Mock implementations

    static class MockAiChatModel implements AiChatModel {
        private final List<AiChatResponse> responses = new ArrayList<>();
        private final List<AiChatRequest> requests = new ArrayList<>();
        private int responseIndex = 0;

        void addResponse(AiChatMessage message) {
            responses.add(new AiChatResponse(
                    message,
                    AiFinishReason.STOP,
                    new AiTokenUsage(10, 5, 15),
                    "resp-" + responses.size(),
                    "mock-model",
                    null
            ));
        }

        void setCustomResponse(AiChatResponse response) {
            responses.add(response);
        }

        void setResponseToReturn(AiChatResponse response) {
            responses.clear();
            responses.add(response);
        }

        @Override
        public AiChatResponse chat(AiChatRequest request) {
            requests.add(request);
            if (responseIndex < responses.size()) {
                return responses.get(responseIndex++);
            }
            // Default: return a simple assistant message
            return new AiChatResponse(
                    AiChatMessage.assistant("Final response"),
                    AiFinishReason.STOP,
                    new AiTokenUsage(10, 5, 15),
                    "resp-default",
                    "mock-model",
                    null
            );
        }

        int getCallCount() {
            return requests.size();
        }

        List<AiChatRequest> getRequests() {
            return requests;
        }
    }

    static class MockAiMemory implements AiMemory {
        private final Object id;
        private final List<AiChatMessage> messages = new ArrayList<>();

        MockAiMemory(Object id) {
            this.id = id;
        }

        @Override
        public Object id() {
            return id;
        }

        @Override
        public List<AiChatMessage> messages() {
            return new ArrayList<>(messages);
        }

        @Override
        public void add(AiChatMessage message) {
            messages.add(message);
        }

        @Override
        public void clear() {
            messages.clear();
        }

        int getMessageCount() {
            return messages.size();
        }

        List<AiChatMessage> getMessages() {
            return messages;
        }
    }

    static class CapturingExecutionEventPort implements ExecutionEventPort {
        private final List<AgentExecutionEvent> events = new ArrayList<>();

        @Override
        public void startExecutionTracking(String sessionId, String userInput) {
        }

        @Override
        public void endExecutionTracking(String sessionId, String finalResult, boolean success) {
        }

        @Override
        public void startExecution(String sessionId, String agentName, String agentType, Object input) {
        }

        @Override
        public void endExecution(String sessionId, String agentName, String agentType, Object output, String status) {
        }

        @Override
        public void recordError(String sessionId, String agentName, String agentType, String error) {
        }

        @Override
        public void recordCustomEvent(AgentExecutionEvent event) {
            events.add(event);
        }

        @Override
        public void addListener(String sessionId, Listener listener) {
        }

        @Override
        public void removeListener(String sessionId, Listener listener) {
        }
    }
}
