package com.openmanus.aiframework.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.aiframework.runtime.model.AiChatMessage;
import com.openmanus.aiframework.runtime.model.AiChatRequest;
import com.openmanus.aiframework.runtime.model.AiChatResponse;
import com.openmanus.aiframework.runtime.model.AiFinishReason;
import com.openmanus.aiframework.runtime.model.AiTokenUsage;
import com.openmanus.aiframework.runtime.model.AiToolCall;
import com.openmanus.aiframework.runtime.model.AiToolResult;
import com.openmanus.aiframework.runtime.model.AiToolSpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiRuntimeModelTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldNormalizeAndValidateToolCall() {
        AiToolCall call = new AiToolCall(" ", "search", null);
        assertNull(call.id());
        assertEquals("search", call.name());
        assertEquals("{}", call.arguments());

        AiToolCall blankArguments = new AiToolCall("call_x", "search", "   ");
        assertEquals("{}", blankArguments.arguments());

        AiToolCall withId = call.withId("call_1");
        assertEquals("call_1", withId.id());
        assertEquals("{}", withId.arguments());

        assertThrows(IllegalArgumentException.class, () -> new AiToolCall("id", " ", "{}"));
        assertThrows(NullPointerException.class, () -> call.withId(null));
        assertThrows(IllegalArgumentException.class, () -> call.withId(" "));
    }

    @Test
    void shouldNormalizeAndValidateToolResultAndToolSpec() throws Exception {
        AiToolResult result = new AiToolResult("call_1", "search", null, false);
        assertEquals("", result.content());

        AiToolSpec spec = new AiToolSpec("search", null, objectMapper.readTree("{\"type\":\"object\"}"));
        assertEquals("", spec.description());
        assertEquals("object", spec.inputSchema().path("type").asText());

        assertThrows(IllegalArgumentException.class, () -> new AiToolResult(" ", "search", "ok", false));
        assertThrows(IllegalArgumentException.class, () -> new AiToolResult("call", " ", "ok", false));
        assertThrows(IllegalArgumentException.class, () -> new AiToolSpec(" ", "desc", null));
    }

    @Test
    void shouldBuildChatMessageFactoriesAndDefensiveCopy() {
        List<AiToolCall> toolCalls = new ArrayList<>();
        toolCalls.add(new AiToolCall("id_1", "search", "{\"q\":\"weather\"}"));

        AiChatMessage assistant = AiChatMessage.assistant("ok", toolCalls);
        toolCalls.add(new AiToolCall("id_2", "fetch", "{}"));

        assertEquals(1, assistant.toolCalls().size());
        assertThrows(UnsupportedOperationException.class,
                () -> assistant.toolCalls().add(new AiToolCall("id_3", "x", "{}")));

        assertEquals(AiChatMessage.Role.SYSTEM, AiChatMessage.system("sys").role());
        assertEquals(AiChatMessage.Role.USER, AiChatMessage.user("u").role());
        assertEquals(AiChatMessage.Role.ASSISTANT, AiChatMessage.assistant("a").role());

        AiChatMessage toolMessage = AiChatMessage.tool(new AiToolResult("call_1", "search", "done", false));
        assertEquals(AiChatMessage.Role.TOOL, toolMessage.role());
        assertEquals("call_1", toolMessage.toolCallId());
        assertEquals("search", toolMessage.name());
        assertEquals("done", toolMessage.content());

        assertThrows(NullPointerException.class, () -> AiChatMessage.tool(null));
        assertThrows(NullPointerException.class,
                () -> new AiChatMessage(null, "content", null, null, List.of()));
    }

    @Test
    void shouldBuildRequestAndResponseWithDefaults() throws Exception {
        AiChatRequest request = new AiChatRequest(
                null,
                null,
                null,
                0.1,
                256,
                30,
                objectMapper.readTree("{\"type\":\"json_schema\"}")
        );
        assertEquals("", request.model());
        assertEquals(0, request.messages().size());
        assertEquals(0, request.toolSpecs().size());

        AiChatRequest requestWithNullEntries = new AiChatRequest(
                "gpt-5.4",
                Arrays.asList(AiChatMessage.user("hello"), null),
                Arrays.asList(new AiToolSpec("search", "Search", objectMapper.readTree("{\"type\":\"object\"}")), null),
                null,
                null,
                null,
                null
        );
        assertEquals(1, requestWithNullEntries.messages().size());
        assertEquals(1, requestWithNullEntries.toolSpecs().size());

        AiChatResponse response = new AiChatResponse(
                AiChatMessage.assistant("done"),
                AiFinishReason.STOP,
                new AiTokenUsage(10, 5, 15),
                " ",
                " ",
                objectMapper.readTree("{\"id\":\"resp_1\"}")
        );
        assertNull(response.responseId());
        assertNull(response.model());
        assertEquals(15, response.tokenUsage().totalTokens());

        assertThrows(NullPointerException.class, () -> new AiChatResponse(
                null, AiFinishReason.OTHER, null, null, null, null));
    }

    @Test
    void shouldUseMemoryDefaultAddAll() {
        TestMemory memory = new TestMemory("conv-1");
        memory.addAll(null);
        memory.addAll(List.of());
        memory.addAll(List.of(AiChatMessage.user("hello"), AiChatMessage.assistant("world")));

        assertEquals("conv-1", memory.id());
        assertEquals(2, memory.messages().size());

        memory.clear();
        assertEquals(0, memory.messages().size());
    }

    private static class TestMemory implements AiMemory {

        private final String id;
        private final List<AiChatMessage> messages = new ArrayList<>();

        private TestMemory(String id) {
            this.id = id;
        }

        @Override
        public Object id() {
            return id;
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
