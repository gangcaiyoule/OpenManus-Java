package com.openmanus.agent.base;

import com.openmanus.aiframework.runtime.AiChatModel;
import com.openmanus.aiframework.runtime.AiMemory;
import com.openmanus.aiframework.runtime.AiMemoryProvider;
import com.openmanus.aiframework.runtime.model.AiChatMessage;
import com.openmanus.aiframework.runtime.model.AiChatRequest;
import com.openmanus.aiframework.runtime.model.AiChatResponse;
import com.openmanus.aiframework.runtime.model.AiToolCall;
import com.openmanus.infra.memory.FileChatMemoryStore;
import com.openmanus.infra.memory.InMemoryAiMemoryStore;
import com.openmanus.infra.memory.PersistentAiMemory;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AbstractAgentExecutorRuntimeEntryTest {

    @Test
    void shouldExecuteWithRuntimeModel() {
        RuntimeEntryAgent agent = RuntimeEntryAgent.builder()
                .aiChatModel(request -> assistant("ok"))
                .build();

        assertEquals("ok", agent.execute("hello-runtime", "conv-runtime"));
    }

    @Test
    void shouldRejectBlankRuntimeInput() {
        RuntimeEntryAgent agent = RuntimeEntryAgent.builder()
                .aiChatModel(request -> assistant("ok"))
                .build();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> agent.execute("   ", "conv-runtime")
        );
        assertEquals("userInput cannot be null or blank", ex.getMessage());
    }

    @Test
    void shouldFailFastWhenNoModelConfigured() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> RuntimeEntryAgent.builder().build()
        );
        assertEquals("aiChatModel must be configured", ex.getMessage());
    }

    @Test
    void shouldSupportRuntimeAiMemoryProviderEntryPoint() {
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        AiMemoryProvider memoryProvider = memoryId -> new PersistentAiMemory(memoryId, store);

        RuntimeEntryAgentWithoutSystem agent = RuntimeEntryAgentWithoutSystem.builder()
                .aiChatModel(request -> assistant("runtime-ok"))
                .aiMemoryProvider(memoryProvider)
                .build();

        String memoryId = "conv-runtime-memory-provider";
        assertEquals("runtime-ok", agent.execute("hello-runtime", memoryId));

        AiMemory memory = memoryProvider.get(memoryId);
        assertEquals(2, memory.messages().size());
        assertEquals(AiChatMessage.Role.USER, memory.messages().get(0).role());
        assertEquals(AiChatMessage.Role.ASSISTANT, memory.messages().get(1).role());
    }

    @Test
    void shouldFailFastWhenAiMemoryProviderReturnsNullMemory() {
        RuntimeEntryAgentWithoutSystem agent = RuntimeEntryAgentWithoutSystem.builder()
                .aiChatModel(request -> assistant("runtime-ok"))
                .aiMemoryProvider(memoryId -> null)
                .build();

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> agent.execute("hello-runtime", "conv-runtime-null-memory")
        );
        assertEquals(
                "aiMemoryProvider returned null for memoryId: conv-runtime-null-memory",
                ex.getMessage()
        );
    }

    @Test
    void shouldNotInvokeRuntimeMemoryProviderWhenMemoryIdIsNull() {
        AtomicInteger runtimeProviderCalls = new AtomicInteger();
        AiMemoryProvider runtimeProvider = memoryId -> {
            runtimeProviderCalls.incrementAndGet();
            return new PersistentAiMemory(memoryId, new InMemoryAiMemoryStore());
        };

        RuntimeEntryAgentWithoutSystem agent = RuntimeEntryAgentWithoutSystem.builder()
                .aiChatModel(request -> assistant("runtime-ok"))
                .aiMemoryProvider(runtimeProvider)
                .build();

        assertEquals("runtime-ok", agent.execute("hello-runtime", null));
        assertEquals(0, runtimeProviderCalls.get());
    }

    @Test
    void shouldBackfillToolCallIdWhenModelOmitsIt() {
        RecordingScriptedRuntimeModel runtimeModel = new RecordingScriptedRuntimeModel(List.of(
                assistantToolCall(null, "echo", "{\"text\":\"hello\"}"),
                assistant("done")
        ));

        RuntimeEntryAgent agent = RuntimeEntryAgent.builder()
                .aiChatModel(runtimeModel)
                .toolFromObject(new EchoTool())
                .build();

        assertEquals("done", agent.execute("run tool", "conv-runtime"));

        List<AiChatMessage> secondRequestMessages = runtimeModel.requests().get(1).messages();
        AiChatMessage assistant = secondRequestMessages.stream()
                .filter(message -> message.role() == AiChatMessage.Role.ASSISTANT)
                .findFirst()
                .orElseThrow();
        AiChatMessage toolResult = secondRequestMessages.stream()
                .filter(message -> message.role() == AiChatMessage.Role.TOOL)
                .findFirst()
                .orElseThrow();

        String assistantToolCallId = assistant.toolCalls().getFirst().id();
        assertFalse(assistantToolCallId == null || assistantToolCallId.isBlank());
        assertEquals(assistantToolCallId, toolResult.toolCallId());
    }

    @Test
    void shouldAbortWhenMaxIterationsExceeded() {
        RuntimeEntryAgent agent = RuntimeEntryAgent.builder()
                .aiChatModel(request -> assistantToolCall("call_1", "echo", "{\"text\":\"x\"}"))
                .maxIterations(1)
                .toolFromObject(new EchoTool())
                .build();

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> agent.execute("run", "conv-runtime")
        );
        assertEquals("Agent exceeded maximum iterations (1)", ex.getMessage());
    }

    @Test
    void shouldAbortWhenUnknownToolCallBatchRepeats() {
        RuntimeEntryAgent agent = RuntimeEntryAgent.builder()
                .aiChatModel(request -> assistantToolCall("call_1", "missingTool", "{}"))
                .build();

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> agent.execute("run", "conv-runtime")
        );
        assertEquals(
                "Agent aborted due to repeated unknown tool-call batch (threshold=3)",
                ex.getMessage()
        );
    }

    @Test
    void shouldFailFastWhenDuplicateToolNamesRegisteredAcrossObjects() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> RuntimeEntryAgent.builder()
                        .aiChatModel(request -> assistant("ok"))
                        .toolFromObject(new DuplicateToolA())
                        .toolFromObject(new DuplicateToolB())
                        .build()
        );
        assertEquals("Duplicate tool name detected: dup_tool", ex.getMessage());
    }

    @Test
    void shouldAbortWhenExecutionSecondsExceeded() {
        RuntimeEntryAgent agent = RuntimeEntryAgent.builder()
                .aiChatModel(request -> assistantToolCall("call_1", "slowEcho", "{\"text\":\"x\"}"))
                .maxExecutionSeconds(1)
                .toolFromObject(new SlowEchoTool())
                .build();

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> agent.execute("run", "conv-runtime")
        );
        assertEquals("Agent exceeded maximum execution seconds (1)", ex.getMessage());
    }

    @Test
    void shouldRejectNullSystemMessage() {
        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> RuntimeEntryAgent.builder().systemMessage(null).build()
        );
        assertEquals("message cannot be null", ex.getMessage());
    }

    @Test
    void shouldIgnoreBlankSystemMessageInsteadOfPersistingIt() throws Exception {
        Path baseDir = Files.createTempDirectory("runtime-entry-blank-system-");
        FileChatMemoryStore store = new FileChatMemoryStore(baseDir);
        AiMemoryProvider memoryProvider = memoryId -> new PersistentAiMemory(memoryId, store);

        RuntimeEntryAgentWithoutSystem agent = RuntimeEntryAgentWithoutSystem.builder()
                .aiChatModel(request -> assistant("runtime-ok"))
                .aiMemoryProvider(memoryProvider)
                .systemMessage("   ")
                .build();

        String memoryId = "conv-runtime-blank-system";
        assertEquals("runtime-ok", agent.execute("hello-runtime", memoryId));

        AiMemory memory = memoryProvider.get(memoryId);
        assertEquals(2, memory.messages().size());
        assertEquals(AiChatMessage.Role.USER, memory.messages().get(0).role());
        assertEquals(AiChatMessage.Role.ASSISTANT, memory.messages().get(1).role());
    }

    private static AiChatResponse assistant(String text) {
        return new AiChatResponse(
                AiChatMessage.assistant(text),
                null,
                null,
                null,
                null,
                null
        );
    }

    private static AiChatResponse assistantToolCall(String id, String name, String arguments) {
        return new AiChatResponse(
                AiChatMessage.assistant("tool", List.of(new AiToolCall(id, name, arguments))),
                null,
                null,
                null,
                null,
                null
        );
    }

    static class RecordingScriptedRuntimeModel implements AiChatModel {
        private final List<AiChatResponse> responses;
        private final List<AiChatRequest> requests = new ArrayList<>();
        private int cursor = 0;

        RecordingScriptedRuntimeModel(List<AiChatResponse> responses) {
            this.responses = responses;
        }

        @Override
        public synchronized AiChatResponse chat(AiChatRequest request) {
            requests.add(request);
            if (cursor >= responses.size()) {
                return assistant("done");
            }
            return responses.get(cursor++);
        }

        List<AiChatRequest> requests() {
            return requests;
        }
    }

    static class RuntimeEntryAgent extends AbstractAgentExecutor<RuntimeEntryAgent.Builder> {

        static class Builder extends AbstractAgentExecutor.Builder<Builder> {
            RuntimeEntryAgent build() {
                this.name("runtime_entry_agent")
                        .description("runtime entry test agent")
                        .singleParameter("input")
                        .systemMessage("you are a test agent");
                return new RuntimeEntryAgent(this);
            }
        }

        static Builder builder() {
            return new Builder();
        }

        RuntimeEntryAgent(Builder builder) {
            super(builder);
        }
    }

    static class RuntimeEntryAgentWithoutSystem
            extends AbstractAgentExecutor<RuntimeEntryAgentWithoutSystem.Builder> {

        static class Builder extends AbstractAgentExecutor.Builder<Builder> {
            RuntimeEntryAgentWithoutSystem build() {
                this.name("runtime_entry_agent_without_system")
                        .description("runtime entry test agent without system")
                        .singleParameter("input");
                return new RuntimeEntryAgentWithoutSystem(this);
            }
        }

        static Builder builder() {
            return new Builder();
        }

        RuntimeEntryAgentWithoutSystem(Builder builder) {
            super(builder);
        }
    }

    static class EchoTool {
        @com.openmanus.aiframework.tool.AiTool
        public String echo(@com.openmanus.aiframework.tool.AiParam("text") String text) {
            return "echo:" + text;
        }
    }

    static class SlowEchoTool {
        @com.openmanus.aiframework.tool.AiTool
        public String slowEcho(@com.openmanus.aiframework.tool.AiParam("text") String text) {
            try {
                Thread.sleep(1100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "slow-echo:" + text;
        }
    }

    static class DuplicateToolA {
        @com.openmanus.aiframework.tool.AiTool(name = "dup_tool")
        public String a(@com.openmanus.aiframework.tool.AiParam("text") String text) {
            return "a:" + text;
        }
    }

    static class DuplicateToolB {
        @com.openmanus.aiframework.tool.AiTool(name = "dup_tool")
        public String b(@com.openmanus.aiframework.tool.AiParam("text") String text) {
            return "b:" + text;
        }
    }
}
