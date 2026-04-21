package com.openmanus.agent.base;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openmanus.aiframework.runtime.AiChatModel;
import com.openmanus.aiframework.runtime.mcp.McpClient;
import com.openmanus.aiframework.runtime.mcp.McpInvokeRequest;
import com.openmanus.aiframework.runtime.mcp.McpToolDefinition;
import com.openmanus.aiframework.runtime.mcp.McpToolResult;
import com.openmanus.aiframework.runtime.model.AiChatMessage;
import com.openmanus.aiframework.runtime.model.AiChatRequest;
import com.openmanus.aiframework.runtime.model.AiChatResponse;
import com.openmanus.aiframework.runtime.model.AiToolCall;
import com.openmanus.aiframework.tool.AiParam;
import com.openmanus.aiframework.tool.AiRegisteredTool;
import com.openmanus.aiframework.tool.AiTool;
import com.openmanus.aiframework.tool.mcp.McpToolRegistryBootstrap;
import com.openmanus.aiframework.tool.mcp.McpToolSpecAdapter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractAgentExecutorMcpIntegrationTest {

    @Test
    void shouldExecuteRegisteredMcpToolAlongsideLocalTools() {
        RecordingScriptedRuntimeModel runtimeModel = new RecordingScriptedRuntimeModel(List.of(
                assistantToolCall("call_local", "local.echo", "{\"text\":\"local\"}"),
                assistantToolCall("call_mcp", "mcp.echo", "{\"text\":\"mcp\"}"),
                assistant("done")
        ));
        AtomicReference<McpInvokeRequest> invoked = new AtomicReference<>();
        McpClient mcpClient = new FakeMcpClient(
                List.of(new McpToolDefinition("mcp.echo", "echo", textSchema("text"))),
                request -> {
                    invoked.set(request);
                    return new McpToolResult("mcp.echo", "mcp-result:" + request.arguments().path("text").asText(), false);
                }
        );

        Map<String, AiRegisteredTool> mcpRegistry = new LinkedHashMap<>();
        new McpToolRegistryBootstrap(mcpClient, new McpToolSpecAdapter()).discoverAndRegister(mcpRegistry);

        McpIntegrationAgent.Builder builder = McpIntegrationAgent.builder()
                .aiChatModel(runtimeModel)
                .toolFromObject(new LocalTools());
        mcpRegistry.values().forEach(builder::tool);
        McpIntegrationAgent agent = builder.build();

        String result = agent.execute("run mcp and local", "conv-mcp");

        assertEquals("done", result);
        assertEquals("mcp.echo", invoked.get().toolName());
        assertEquals("mcp", invoked.get().arguments().path("text").asText());

        List<AiChatMessage> secondCallMessages = runtimeModel.requests().get(1).messages();
        assertTrue(secondCallMessages.stream()
                        .filter(message -> message.role() == AiChatMessage.Role.TOOL)
                        .anyMatch(message -> "local.echo".equals(message.name())
                                && "local-result:local".equals(message.content())),
                "local 工具结果应保留在执行链路中");

        List<AiChatMessage> thirdCallMessages = runtimeModel.requests().get(2).messages();
        assertTrue(thirdCallMessages.stream()
                        .filter(message -> message.role() == AiChatMessage.Role.TOOL)
                        .anyMatch(message -> "mcp.echo".equals(message.name())
                                && "mcp-result:mcp".equals(message.content())),
                "MCP 工具结果应以现有 TOOL 观察消息回写到执行链路");
    }

    @Test
    void shouldFailThroughExistingErrorChannelWhenMcpClientThrows() {
        RecordingScriptedRuntimeModel runtimeModel = new RecordingScriptedRuntimeModel(List.of(
                assistantToolCall("call_mcp", "mcp.boom", "{}")
        ));
        McpClient mcpClient = new FakeMcpClient(
                List.of(new McpToolDefinition("mcp.boom", "boom", textSchema("x"))),
                request -> {
                    throw new IllegalStateException("mcp invoke failure");
                }
        );

        Map<String, AiRegisteredTool> mcpRegistry = new LinkedHashMap<>();
        new McpToolRegistryBootstrap(mcpClient, new McpToolSpecAdapter()).discoverAndRegister(mcpRegistry);

        McpIntegrationAgent.Builder builder = McpIntegrationAgent.builder().aiChatModel(runtimeModel);
        mcpRegistry.values().forEach(builder::tool);
        McpIntegrationAgent agent = builder.build();

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> agent.execute("run mcp", "conv-mcp-failure")
        );
        assertEquals("mcp invoke failure", ex.getMessage());
    }

    private static ObjectNode textSchema(String field) {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.putObject("properties").putObject(field).put("type", "string");
        schema.putArray("required").add(field);
        return schema;
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

    private record FakeMcpClient(
            List<McpToolDefinition> tools,
            McpInvokeHandler handler
    ) implements McpClient {

        @Override
        public List<McpToolDefinition> discoverTools() {
            return tools;
        }

        @Override
        public McpToolResult invoke(McpInvokeRequest request) {
            return handler.invoke(request);
        }
    }

    @FunctionalInterface
    private interface McpInvokeHandler {
        McpToolResult invoke(McpInvokeRequest request);
    }

    static class LocalTools {
        @AiTool(name = "local.echo", value = "local echo")
        public String echo(@AiParam("text") String text) {
            return "local-result:" + text;
        }
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

    static class McpIntegrationAgent extends AbstractAgentExecutor<McpIntegrationAgent.Builder> {

        static class Builder extends AbstractAgentExecutor.Builder<Builder> {
            McpIntegrationAgent build() {
                this.name("mcp_integration_agent")
                        .description("mcp integration test agent")
                        .singleParameter("input")
                        .systemMessage("you are a test agent");
                return new McpIntegrationAgent(this);
            }
        }

        static Builder builder() {
            return new Builder();
        }

        private McpIntegrationAgent(Builder builder) {
            super(builder);
        }
    }
}
