package com.openmanus.infra.config;

import com.openmanus.agent.impl.unified.UnifiedAgent;
import com.openmanus.aiframework.runtime.AiChatModel;
import com.openmanus.aiframework.runtime.AiMemory;
import com.openmanus.aiframework.runtime.AiMemoryProvider;
import com.openmanus.aiframework.runtime.model.AiChatMessage;
import com.openmanus.aiframework.runtime.model.AiChatRequest;
import com.openmanus.aiframework.runtime.model.AiChatResponse;
import com.openmanus.aiframework.runtime.model.AiToolCall;
import com.openmanus.aiframework.runtime.AiToolResultArtifactStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UnifiedAgentConfigModelContextTest {

    @Test
    void shouldApplyModelContextLimitFromPropertiesToUnifiedAgent() {
        AiChatModel chatModel = mock(AiChatModel.class);
        when(chatModel.chat(any(AiChatRequest.class)))
                .thenReturn(new AiChatResponse(AiChatMessage.assistant("ok"), null, null, null, null, null));

        AiMemoryProvider memoryProvider = buildMemoryProvider();

        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setModelContextMaxMessages(1);

        UnifiedAgent agent = new UnifiedAgentConfig().unifiedAgent(
                chatModel,
                memoryProvider,
                properties,
                mock(AiToolResultArtifactStore.class),
                null,
                null,
                null,
                null
        );

        String memoryId = "conv-unified-config-model-context";
        AiMemory memory = memoryProvider.get(memoryId);
        memory.add(AiChatMessage.system("you are a test agent"));
        memory.add(AiChatMessage.user("old-1"));
        memory.add(AiChatMessage.assistant("old-2"));

        String result = agent.execute("new-question", memoryId);
        assertEquals("ok", result);

        ArgumentCaptor<AiChatRequest> requestCaptor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(chatModel, times(1)).chat(requestCaptor.capture());

        List<AiChatMessage> modelMessages = requestCaptor.getValue().messages();
        String modelPayload = modelMessages.toString();
        assertTrue(modelPayload.contains("new-question"));
        assertTrue(modelPayload.contains("you are a test agent"));
        assertEquals(3, modelMessages.size());
        assertTrue(modelMessages.stream().anyMatch(message ->
                message.role() == AiChatMessage.Role.ASSISTANT
                        && message.content().contains("[Historical Key Memory]")));
    }

    @Test
    void shouldApplyReactMaxIterationsFromPropertiesToUnifiedAgent() {
        AiChatModel chatModel = mock(AiChatModel.class);
        AiToolCall toolCall = new AiToolCall("", "unknown_tool", "{}");
        when(chatModel.chat(any(AiChatRequest.class)))
                .thenReturn(new AiChatResponse(AiChatMessage.assistant(null, List.of(toolCall)), null, null, null, null, null));

        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setReactMaxIterations(1);

        UnifiedAgent agent = new UnifiedAgentConfig().unifiedAgent(
                chatModel,
                buildMemoryProvider(),
                properties,
                mock(AiToolResultArtifactStore.class),
                null,
                null,
                null,
                null
        );

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> agent.execute("new-question", "conv-unified-config-react-iter"));
        assertTrue(ex.getMessage().contains("Agent exceeded maximum iterations (1)"));
        verify(chatModel, times(1)).chat(any(AiChatRequest.class));
    }

    @Test
    void shouldKeepCurrentUserWhenTotalLimitIsOneFromProperties() {
        AiChatModel chatModel = mock(AiChatModel.class);
        when(chatModel.chat(any(AiChatRequest.class)))
                .thenReturn(new AiChatResponse(AiChatMessage.assistant("ok"), null, null, null, null, null));

        AiMemoryProvider memoryProvider = buildMemoryProvider();
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setModelContextMaxMessages(0);
        properties.getChatMemory().setModelContextMaxTotalMessages(1);

        UnifiedAgent agent = new UnifiedAgentConfig().unifiedAgent(
                chatModel,
                memoryProvider,
                properties,
                mock(AiToolResultArtifactStore.class),
                null,
                null,
                null,
                null
        );

        String memoryId = "conv-unified-config-model-context-total-one";
        AiMemory memory = memoryProvider.get(memoryId);
        memory.add(AiChatMessage.system("you are a test agent"));
        memory.add(AiChatMessage.user("old-1"));
        memory.add(AiChatMessage.assistant("old-2"));

        String result = agent.execute("new-question", memoryId);
        assertEquals("ok", result);

        ArgumentCaptor<AiChatRequest> requestCaptor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(chatModel, times(1)).chat(requestCaptor.capture());
        String modelPayload = requestCaptor.getValue().messages().toString();
        assertTrue(modelPayload.contains("new-question"));
        assertFalse(modelPayload.contains("you are a test agent"));
        assertEquals(1, requestCaptor.getValue().messages().size());
    }

    @Test
    void shouldApplyModelContextTotalLimitFromPropertiesToUnifiedAgentDuringToolLoop() {
        AiToolCall toolCall = new AiToolCall("", "unknown_tool", "{}");
        AiChatModel chatModel = mockChatModel(
                new AiChatResponse(AiChatMessage.assistant(null, List.of(toolCall)), null, null, null, null, null),
                new AiChatResponse(AiChatMessage.assistant("ok"), null, null, null, null, null)
        );

        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setModelContextMaxMessages(0);
        properties.getChatMemory().setModelContextMaxTotalMessages(2);

        UnifiedAgent agent = new UnifiedAgentConfig().unifiedAgent(
                chatModel,
                buildMemoryProvider(),
                properties,
                mock(AiToolResultArtifactStore.class),
                null,
                null,
                null,
                null
        );

        String result = agent.execute("new-question", "conv-unified-config-model-context-total");
        assertEquals("ok", result);

        ArgumentCaptor<AiChatRequest> requestCaptor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(chatModel, times(2)).chat(requestCaptor.capture());
        assertTrue(requestCaptor.getAllValues().get(0).messages().size() <= 2);
        assertTrue(requestCaptor.getAllValues().get(1).messages().size() <= 2);
    }

    @Test
    void shouldApplyModelContextTotalLimitFromPropertiesToUnifiedAgent() {
        AiToolCall toolCall = new AiToolCall("", "unknown_tool", "{}");
        AiChatModel chatModel = mockChatModel(
                new AiChatResponse(AiChatMessage.assistant(null, List.of(toolCall)), null, null, null, null, null),
                new AiChatResponse(AiChatMessage.assistant("ok"), null, null, null, null, null)
        );

        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setModelContextMaxMessages(2);
        properties.getChatMemory().setModelContextMaxTotalMessages(3);

        UnifiedAgent agent = new UnifiedAgentConfig().unifiedAgent(
                chatModel,
                buildMemoryProvider(),
                properties,
                mock(AiToolResultArtifactStore.class),
                null,
                null,
                null,
                null
        );

        String result = agent.execute("new-question", "conv-unified-config-total-context");
        assertEquals("ok", result);

        ArgumentCaptor<AiChatRequest> requestCaptor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(chatModel, times(2)).chat(requestCaptor.capture());
        assertTrue(requestCaptor.getAllValues().get(0).messages().size() <= 3);
        assertTrue(requestCaptor.getAllValues().get(1).messages().size() <= 3);
    }

    @Test
    void shouldClampNegativeModelContextLimitsBeforeWiringToUnifiedAgent() {
        AiChatModel chatModel = mock(AiChatModel.class);
        when(chatModel.chat(any(AiChatRequest.class)))
                .thenReturn(new AiChatResponse(AiChatMessage.assistant("ok"), null, null, null, null, null));

        AiMemoryProvider memoryProvider = buildMemoryProvider();
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setModelContextMaxMessages(-1);
        properties.getChatMemory().setModelContextMaxTotalMessages(-1);

        UnifiedAgent agent = new UnifiedAgentConfig().unifiedAgent(
                chatModel,
                memoryProvider,
                properties,
                mock(AiToolResultArtifactStore.class),
                null,
                null,
                null,
                null
        );

        String memoryId = "conv-unified-config-negative-context";
        AiMemory memory = memoryProvider.get(memoryId);
        memory.add(AiChatMessage.system("you are a test agent"));
        memory.add(AiChatMessage.user("old-1"));
        memory.add(AiChatMessage.assistant("old-2"));
        memory.add(AiChatMessage.user("old-3"));

        String result = agent.execute("new-question", memoryId);
        assertEquals("ok", result);

        ArgumentCaptor<AiChatRequest> requestCaptor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(chatModel, times(1)).chat(requestCaptor.capture());
        String payload = requestCaptor.getValue().messages().toString();
        assertTrue(payload.contains("you are a test agent"));
        assertTrue(payload.contains("old-1"));
        assertTrue(payload.contains("old-2"));
        assertTrue(payload.contains("old-3"));
        assertTrue(payload.contains("new-question"));
    }

    @Test
    void shouldApplyApproxTokenLimitFromPropertiesToUnifiedAgent() {
        AiChatModel chatModel = mock(AiChatModel.class);
        when(chatModel.chat(any(AiChatRequest.class)))
                .thenReturn(new AiChatResponse(AiChatMessage.assistant("ok"), null, null, null, null, null));

        AiMemoryProvider memoryProvider = buildMemoryProvider();
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setModelContextMaxMessages(0);
        properties.getChatMemory().setModelContextMaxTotalMessages(0);
        properties.getChatMemory().setModelContextMaxApproxTokens(120);

        UnifiedAgent agent = new UnifiedAgentConfig().unifiedAgent(
                chatModel,
                memoryProvider,
                properties,
                mock(AiToolResultArtifactStore.class),
                null,
                null,
                null,
                null
        );

        String memoryId = "conv-unified-config-approx-token";
        AiMemory memory = memoryProvider.get(memoryId);
        memory.add(AiChatMessage.system("you are a test agent"));
        memory.add(AiChatMessage.user("old-large-1-" + "A".repeat(1000)));
        memory.add(AiChatMessage.assistant("old-large-2-" + "B".repeat(1000)));
        memory.add(AiChatMessage.user("recent-small"));

        String result = agent.execute("new-question", memoryId);
        assertEquals("ok", result);

        ArgumentCaptor<AiChatRequest> requestCaptor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(chatModel, times(1)).chat(requestCaptor.capture());
        String payload = requestCaptor.getValue().messages().toString();
        assertTrue(payload.contains("new-question"));
        assertTrue(payload.contains("you are a test agent"));
        assertTrue(payload.contains("recent-small"));
        assertFalse(payload.contains("old-large-1-"));
        assertFalse(payload.contains("old-large-2-"));
    }

    @Test
    void shouldApplyTokenizerBudgetWhenTokenizerModeConfigured() {
        AiChatModel chatModel = mock(AiChatModel.class);
        when(chatModel.chat(any(AiChatRequest.class)))
                .thenReturn(new AiChatResponse(AiChatMessage.assistant("ok"), null, null, null, null, null));

        AiMemoryProvider memoryProvider = buildMemoryProvider();
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setModelContextMaxMessages(0);
        properties.getChatMemory().setModelContextMaxTotalMessages(0);
        properties.getChatMemory().setModelContextMaxApproxTokens(120);
        properties.getChatMemory().setModelContextTokenCountMode("tokenizer");

        UnifiedAgent agent = new UnifiedAgentConfig().unifiedAgent(
                chatModel,
                memoryProvider,
                properties,
                mock(AiToolResultArtifactStore.class),
                null,
                null,
                null,
                null
        );

        String memoryId = "conv-unified-config-tokenizer-budget";
        AiMemory memory = memoryProvider.get(memoryId);
        memory.add(AiChatMessage.system("you are a test agent"));
        memory.add(AiChatMessage.user("old-large-1-" + "A".repeat(1000)));
        memory.add(AiChatMessage.assistant("old-large-2-" + "B".repeat(1000)));
        memory.add(AiChatMessage.user("recent-small"));

        String result = agent.execute("new-question", memoryId);
        assertEquals("ok", result);

        ArgumentCaptor<AiChatRequest> requestCaptor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(chatModel, times(1)).chat(requestCaptor.capture());
        String payload = requestCaptor.getValue().messages().toString();
        assertTrue(payload.contains("new-question"));
        assertTrue(payload.contains("recent-small"));
        assertFalse(payload.contains("old-large-1-"));
        assertFalse(payload.contains("old-large-2-"));
    }

    private static AiChatModel mockChatModel(AiChatResponse... responses) {
        AiChatModel chatModel = mock(AiChatModel.class);
        if (responses == null || responses.length == 0) {
            return chatModel;
        }
        AiChatResponse first = responses[0];
        AiChatResponse[] rest = Arrays.copyOfRange(responses, 1, responses.length);
        when(chatModel.chat(any(AiChatRequest.class))).thenReturn(first, rest);
        return chatModel;
    }

    private static AiMemoryProvider buildMemoryProvider() {
        Map<Object, AiMemory> store = new ConcurrentHashMap<>();
        return memoryId -> store.computeIfAbsent(memoryId, InMemoryAiMemory::new);
    }

    private static final class InMemoryAiMemory implements AiMemory {

        private final Object memoryId;
        private final List<AiChatMessage> messages = new ArrayList<>();

        private InMemoryAiMemory(Object memoryId) {
            this.memoryId = memoryId;
        }

        @Override
        public Object id() {
            return memoryId;
        }

        @Override
        public List<AiChatMessage> messages() {
            return List.copyOf(messages);
        }

        @Override
        public void add(AiChatMessage message) {
            messages.add(message);
        }

        @Override
        public void clear() {
            messages.clear();
        }
    }
}
