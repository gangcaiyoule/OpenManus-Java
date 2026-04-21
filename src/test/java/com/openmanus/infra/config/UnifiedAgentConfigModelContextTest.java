package com.openmanus.infra.config;

import com.openmanus.agent.impl.unified.UnifiedAgent;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());

        InMemoryChatMemoryStore store = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(200)
                .chatMemoryStore(store)
                .build();

        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setModelContextMaxMessages(1);

        UnifiedAgent agent = new UnifiedAgentConfig().unifiedAgent(
                chatModel,
                memoryProvider,
                properties,
                null,
                null,
                null,
                null
        );

        String memoryId = "conv-unified-config-model-context";
        ChatMemory memory = memoryProvider.get(memoryId);
        memory.add(SystemMessage.from("you are a test agent"));
        memory.add(UserMessage.from("old-1"));
        memory.add(AiMessage.from("old-2"));

        String result = agent.execute(ToolExecutionRequest.builder()
                .name("unified_agent")
                .arguments("new-question")
                .build(), memoryId);
        assertEquals("ok", result);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(1)).chat(requestCaptor.capture());

        String modelPayload = requestCaptor.getValue().messages().toString();
        assertTrue(modelPayload.contains("new-question"));
        assertTrue(modelPayload.contains("you are a test agent"));
        assertFalse(modelPayload.contains("old-1"));
        assertFalse(modelPayload.contains("old-2"));
        assertEquals(2, requestCaptor.getValue().messages().size());
    }

    @Test
    void shouldApplyModelContextTotalLimitFromPropertiesToUnifiedAgentDuringToolLoop() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());

        InMemoryChatMemoryStore store = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(200)
                .chatMemoryStore(store)
                .build();

        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setModelContextMaxMessages(0);
        properties.getChatMemory().setModelContextMaxTotalMessages(2);

        UnifiedAgent agent = new UnifiedAgentConfig().unifiedAgent(
                chatModel,
                memoryProvider,
                properties,
                null,
                null,
                null,
                null
        );

        String memoryId = "conv-unified-config-model-context-total";
        ChatMemory memory = memoryProvider.get(memoryId);
        memory.add(SystemMessage.from("you are a test agent"));
        memory.add(UserMessage.from("old-1"));
        memory.add(AiMessage.from("old-2"));

        String result = agent.execute(ToolExecutionRequest.builder()
                .name("unified_agent")
                .arguments("new-question")
                .build(), memoryId);
        assertEquals("ok", result);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(1)).chat(requestCaptor.capture());

        String modelPayload = requestCaptor.getValue().messages().toString();
        assertTrue(modelPayload.contains("new-question"));
        assertTrue(modelPayload.contains("you are a test agent"));
        assertFalse(modelPayload.contains("old-1"));
        assertFalse(modelPayload.contains("old-2"));
        assertEquals(2, requestCaptor.getValue().messages().size());
    }

    @Test
    void shouldKeepCurrentUserWhenTotalLimitIsOneFromProperties() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());

        InMemoryChatMemoryStore store = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(200)
                .chatMemoryStore(store)
                .build();

        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setModelContextMaxMessages(0);
        properties.getChatMemory().setModelContextMaxTotalMessages(1);

        UnifiedAgent agent = new UnifiedAgentConfig().unifiedAgent(
                chatModel,
                memoryProvider,
                properties,
                null,
                null,
                null,
                null
        );

        String memoryId = "conv-unified-config-model-context-total-one";
        ChatMemory memory = memoryProvider.get(memoryId);
        memory.add(SystemMessage.from("you are a test agent"));
        memory.add(UserMessage.from("old-1"));
        memory.add(AiMessage.from("old-2"));

        String result = agent.execute(ToolExecutionRequest.builder()
                .name("unified_agent")
                .arguments("new-question")
                .build(), memoryId);
        assertEquals("ok", result);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(1)).chat(requestCaptor.capture());
        String modelPayload = requestCaptor.getValue().messages().toString();
        assertTrue(modelPayload.contains("new-question"));
        assertFalse(modelPayload.contains("you are a test agent"));
        assertEquals(1, requestCaptor.getValue().messages().size());
    }

    @Test
    void shouldApplyModelContextTotalLimitFromPropertiesToUnifiedAgent() {
        ChatModel chatModel = mock(ChatModel.class);
        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .name("unknown_tool")
                .arguments("{}")
                .build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(toolCall)).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());

        InMemoryChatMemoryStore store = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(200)
                .chatMemoryStore(store)
                .build();

        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setModelContextMaxMessages(2);
        properties.getChatMemory().setModelContextMaxTotalMessages(3);

        UnifiedAgent agent = new UnifiedAgentConfig().unifiedAgent(
                chatModel,
                memoryProvider,
                properties,
                null,
                null,
                null,
                null
        );

        String result = agent.execute(ToolExecutionRequest.builder()
                .name("unified_agent")
                .arguments("new-question")
                .build(), "conv-unified-config-total-context");
        assertEquals("ok", result);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(2)).chat(requestCaptor.capture());
        assertTrue(requestCaptor.getAllValues().get(0).messages().size() <= 3);
        assertTrue(requestCaptor.getAllValues().get(1).messages().size() <= 3);
    }

    @Test
    void shouldClampNegativeModelContextLimitsBeforeWiringToUnifiedAgent() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());

        InMemoryChatMemoryStore store = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(200)
                .chatMemoryStore(store)
                .build();

        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setModelContextMaxMessages(-1);
        properties.getChatMemory().setModelContextMaxTotalMessages(-1);

        UnifiedAgent agent = new UnifiedAgentConfig().unifiedAgent(
                chatModel,
                memoryProvider,
                properties,
                null,
                null,
                null,
                null
        );

        String memoryId = "conv-unified-config-negative-context";
        ChatMemory memory = memoryProvider.get(memoryId);
        memory.add(SystemMessage.from("you are a test agent"));
        memory.add(UserMessage.from("old-1"));
        memory.add(AiMessage.from("old-2"));
        memory.add(UserMessage.from("old-3"));

        String result = agent.execute(ToolExecutionRequest.builder()
                .name("unified_agent")
                .arguments("new-question")
                .build(), memoryId);
        assertEquals("ok", result);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(1)).chat(requestCaptor.capture());
        String payload = requestCaptor.getValue().messages().toString();
        assertTrue(payload.contains("you are a test agent"));
        assertTrue(payload.contains("old-1"));
        assertTrue(payload.contains("old-2"));
        assertTrue(payload.contains("old-3"));
        assertTrue(payload.contains("new-question"));
    }

    @Test
    void shouldApplyReactMaxIterationsFromPropertiesToUnifiedAgent() {
        ChatModel chatModel = mock(ChatModel.class);
        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .name("unknown_tool")
                .arguments("{}")
                .build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(toolCall)).build());

        InMemoryChatMemoryStore store = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(200)
                .chatMemoryStore(store)
                .build();

        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setReactMaxIterations(1);

        UnifiedAgent agent = new UnifiedAgentConfig().unifiedAgent(
                chatModel,
                memoryProvider,
                properties,
                null,
                null,
                null,
                null
        );

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> agent.execute(ToolExecutionRequest.builder()
                        .name("unified_agent")
                        .arguments("new-question")
                        .build(), "conv-unified-config-react-iter"));
        assertTrue(ex.getMessage().contains("maximum iterations (1)"));
        verify(chatModel, times(1)).chat(any(ChatRequest.class));
    }

    @Test
    void shouldApplyApproxTokenLimitFromPropertiesToUnifiedAgent() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());

        InMemoryChatMemoryStore store = new InMemoryChatMemoryStore();
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(200)
                .chatMemoryStore(store)
                .build();

        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setModelContextMaxMessages(0);
        properties.getChatMemory().setModelContextMaxTotalMessages(0);
        properties.getChatMemory().setModelContextMaxApproxTokens(120);

        UnifiedAgent agent = new UnifiedAgentConfig().unifiedAgent(
                chatModel,
                memoryProvider,
                properties,
                null,
                null,
                null,
                null
        );

        String memoryId = "conv-unified-config-approx-token";
        ChatMemory memory = memoryProvider.get(memoryId);
        memory.add(SystemMessage.from("you are a test agent"));
        memory.add(UserMessage.from("old-large-1-" + "A".repeat(1000)));
        memory.add(AiMessage.from("old-large-2-" + "B".repeat(1000)));
        memory.add(UserMessage.from("recent-small"));

        String result = agent.execute(ToolExecutionRequest.builder()
                .name("unified_agent")
                .arguments("new-question")
                .build(), memoryId);
        assertEquals("ok", result);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(1)).chat(requestCaptor.capture());
        String payload = requestCaptor.getValue().messages().toString();
        assertTrue(payload.contains("new-question"));
        assertTrue(payload.contains("you are a test agent"));
        assertTrue(payload.contains("recent-small"));
        assertFalse(payload.contains("old-large-1-"));
        assertFalse(payload.contains("old-large-2-"));
    }
}
