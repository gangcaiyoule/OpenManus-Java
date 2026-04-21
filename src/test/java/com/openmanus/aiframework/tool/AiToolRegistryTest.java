package com.openmanus.aiframework.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.openmanus.aiframework.runtime.model.AiToolSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiToolRegistryTest {

    @Test
    void shouldScanAndExecuteAnnotatedTools() {
        List<AiRegisteredTool> tools = AiToolRegistry.scan(new BasicToolSet());
        assertEquals(2, tools.size());

        AiRegisteredTool sum = tools.stream()
                .filter(tool -> "sum".equals(tool.name()))
                .findFirst()
                .orElseThrow();

        assertEquals("求和工具", sum.description());
        JsonNode schema = sum.parameters().schema();
        assertNotNull(schema.path("properties"));
        assertEquals(2, schema.path("properties").size());
        assertEquals(2, schema.path("required").size());
        assertEquals("integer", schema.path("properties").path("a").path("type").asText());
        assertEquals("integer", schema.path("properties").path("b").path("type").asText());

        String result = sum.executor().execute(new AiToolExecutionRequest("call_1", "sum", "{\"a\":2,\"b\":3}"), "mem_1");
        assertEquals("5", result);
    }

    @Test
    void shouldUseCompiledParameterNameWhenAiParamMissing() {
        List<AiRegisteredTool> tools = AiToolRegistry.scan(new DefaultParamToolSet());
        AiRegisteredTool tool = tools.getFirst();

        String parameterName = tool.parameters().schema().path("properties").fieldNames().next();
        String args = "{\"" + parameterName + "\":\"hello\"}";
        String result = tool.executor().execute(new AiToolExecutionRequest(null, tool.name(), args), "mem");

        assertEquals("hello", result);
    }

    @Test
    void shouldInjectRequestAndMemoryIdWhenDeclared() {
        List<AiRegisteredTool> tools = AiToolRegistry.scan(new ContextAwareToolSet());
        AiRegisteredTool tool = tools.getFirst();

        String result = tool.executor().execute(
                new AiToolExecutionRequest("call_ctx", "contextEcho", "{}"),
                "memory-123"
        );

        assertEquals("contextEcho|call_ctx|memory-123", result);
    }

    @Test
    void shouldNotExposeInternalRuntimeParametersInSchema() {
        List<AiRegisteredTool> tools = AiToolRegistry.scan(new ContextAwareToolSet());
        JsonNode schema = tools.getFirst().parameters().schema();

        assertEquals(0, schema.path("properties").size());
        assertEquals(0, schema.path("required").size());
    }

    @Test
    void shouldInjectMemoryIdForNumericParameterType() {
        List<AiRegisteredTool> tools = AiToolRegistry.scan(new NumericMemoryIdToolSet());
        AiRegisteredTool tool = tools.getFirst();

        String result = tool.executor().execute(
                new AiToolExecutionRequest("call_numeric", "memoryAsLong", "{}"),
                42L
        );

        assertEquals("42", result);
    }

    @Test
    void shouldConvertPrimitiveAndEnumTypes() {
        List<AiRegisteredTool> tools = AiToolRegistry.scan(new AdvancedTypeToolSet());
        AiRegisteredTool tool = tools.getFirst();
        JsonNode schema = tool.parameters().schema();
        assertEquals("boolean", schema.path("properties").path("ok").path("type").asText());
        assertEquals("string", schema.path("properties").path("mode").path("type").asText());
        assertEquals("number", schema.path("properties").path("ratio").path("type").asText());

        String result = tool.executor().execute(
                new AiToolExecutionRequest("id", "typed", "{\"ok\":true,\"mode\":\"FAST\",\"ratio\":1.5}"),
                "mem"
        );

        assertEquals("true|FAST|1.5", result);
    }

    @Test
    void shouldExposeCustomParameterAsObjectSchema() {
        List<AiRegisteredTool> tools = AiToolRegistry.scan(new ObjectParamToolSet());
        AiRegisteredTool tool = tools.getFirst();
        JsonNode schema = tool.parameters().schema();

        assertEquals("object", schema.path("properties").path("payload").path("type").asText());

        String result = tool.executor().execute(
                new AiToolExecutionRequest("id", "acceptObject", "{\"payload\":{\"keyword\":\"java\",\"limit\":3}}"),
                "mem"
        );

        assertEquals("java|3", result);
    }

    @Test
    void shouldUseDefaultValuesForMissingPrimitiveArguments() {
        List<AiRegisteredTool> tools = AiToolRegistry.scan(new PrimitiveDefaultsToolSet());
        AiRegisteredTool tool = tools.getFirst();

        String result = tool.executor().execute(new AiToolExecutionRequest("id", "defaults", "{}"), "mem");
        assertEquals("0|false", result);
    }

    @Test
    void shouldFailWhenRequiredArgumentIsMissing() {
        List<AiRegisteredTool> tools = AiToolRegistry.scan(new RequiredParamToolSet());
        AiRegisteredTool tool = tools.getFirst();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> tool.executor().execute(new AiToolExecutionRequest("id", "requiredText", "{}"), "mem"));
        assertTrue(ex.getMessage().contains("Missing required tool argument 'text'"));
    }

    @Test
    void shouldRejectDuplicateToolNames() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> AiToolRegistry.scan(new DuplicateToolSet()));
        assertTrue(ex.getMessage().contains("Duplicate tool name detected"));
    }

    @Test
    void shouldRejectDuplicateToolParameterNames() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> AiToolRegistry.scan(new DuplicateParamNameToolSet()));
        assertTrue(ex.getMessage().contains("Duplicate tool parameter name detected"));
    }

    @Test
    void shouldWrapInvocationErrors() {
        List<AiRegisteredTool> tools = AiToolRegistry.scan(new FailingToolSet());
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tools.getFirst().executor().execute(new AiToolExecutionRequest("id", "boom", "{}"), "mem"));
        assertTrue(ex.getMessage().contains("Tool execution failed"));
    }

    @Test
    void shouldNormalizeToolExecutionRequest() {
        AiToolExecutionRequest request = new AiToolExecutionRequest("  ", "echo", "   ");
        assertNull(request.id());
        assertEquals("{}", request.arguments());
        assertThrows(IllegalArgumentException.class, () -> new AiToolExecutionRequest("id", " ", "{}"));
    }

    @Test
    void shouldBuildRuntimeToolSpecificationsFromRegisteredTools() {
        List<AiRegisteredTool> tools = AiToolRegistry.scan(new BasicToolSet());
        List<AiToolSpec> specs = AiToolRegistry.toRuntimeToolSpecifications(tools);

        assertEquals(2, specs.size());
        assertTrue(specs.stream().anyMatch(spec -> "sum".equals(spec.name())));
        assertTrue(specs.stream().allMatch(spec -> !spec.description().isBlank()));
    }

    static class BasicToolSet {
        @AiTool(value = "求和工具", name = "sum")
        public String add(@AiParam("参数a") int a, @AiParam("参数b") int b) {
            return String.valueOf(a + b);
        }

        @AiTool("回显")
        public String echo(@AiParam("文本") String text) {
            return text;
        }
    }

    static class DefaultParamToolSet {
        @AiTool("默认参数名")
        public String keep(String text) {
            return text;
        }
    }

    static class ContextAwareToolSet {
        @AiTool
        public String contextEcho(AiToolExecutionRequest request, @AiParam(name = "memoryId", required = false) String memoryId) {
            return request.name() + "|" + request.id() + "|" + memoryId;
        }
    }

    static class AdvancedTypeToolSet {
        enum Mode {
            FAST,
            SAFE
        }

        @AiTool
        public String typed(@AiParam("ok") boolean ok,
                            @AiParam("mode") Mode mode,
                            @AiParam("ratio") double ratio) {
            return ok + "|" + mode + "|" + ratio;
        }
    }

    static class NumericMemoryIdToolSet {
        @AiTool
        public String memoryAsLong(@AiParam(name = "memoryId") long memoryId) {
            return String.valueOf(memoryId);
        }
    }

    static class ObjectParamToolSet {
        static class Payload {
            String keyword;
            int limit;
        }

        @AiTool
        public String acceptObject(@AiParam("payload") Payload payload) {
            return payload.keyword + "|" + payload.limit;
        }
    }

    static class PrimitiveDefaultsToolSet {
        @AiTool
        public String defaults(@AiParam(value = "count", required = false) int count,
                               @AiParam(value = "ok", required = false) boolean ok) {
            return count + "|" + ok;
        }
    }

    static class RequiredParamToolSet {
        @AiTool
        public String requiredText(@AiParam("text") String text) {
            return text;
        }
    }

    static class DuplicateToolSet {
        @AiTool(name = "dup")
        public String first() {
            return "first";
        }

        @AiTool(name = "dup")
        public String second() {
            return "second";
        }
    }

    static class DuplicateParamNameToolSet {
        @AiTool(name = "dupParam")
        public String run(@AiParam(name = "value") String left,
                          @AiParam(name = "value") String right) {
            return left + right;
        }
    }

    static class FailingToolSet {
        @AiTool
        public String boom() {
            throw new IllegalStateException("boom");
        }
    }
}
