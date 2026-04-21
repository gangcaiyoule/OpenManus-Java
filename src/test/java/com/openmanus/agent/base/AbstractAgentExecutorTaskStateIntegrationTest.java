package com.openmanus.agent.base;

import com.openmanus.aiframework.runtime.AiChatModel;
import com.openmanus.aiframework.runtime.AiMemoryProvider;
import com.openmanus.aiframework.runtime.model.AiChatMessage;
import com.openmanus.aiframework.runtime.model.AiChatRequest;
import com.openmanus.aiframework.runtime.model.AiChatResponse;
import com.openmanus.aiframework.runtime.model.AiToolCall;
import com.openmanus.infra.memory.InMemoryAiMemoryStore;
import com.openmanus.infra.memory.PersistentAiMemory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractAgentExecutorTaskStateIntegrationTest {

    @Test
    void shouldInjectTaskStateIntoNextRoundModelInput() {
        RecordingScriptedRuntimeModel runtimeModel = new RecordingScriptedRuntimeModel(List.of(
                assistantToolCall("call_1", "readFile", "{\"path\":\"a.txt\"}", "plan: read and summarize"),
                assistant("done")
        ));
        AiMemoryProvider memoryProvider = memoryId -> new PersistentAiMemory(memoryId, new InMemoryAiMemoryStore());
        TaskStateAgent agent = TaskStateAgent.builder()
                .aiChatModel(runtimeModel)
                .aiMemoryProvider(memoryProvider)
                .toolFromObject(new FileReadTool())
                .build();

        assertEquals("done", agent.execute("please read file", "conv-task-state-ok"));
        List<AiChatMessage> secondRound = runtimeModel.requests().get(1).messages();
        String payload = secondRound.toString();
        assertTrue(payload.contains("[Task State]"), "第二轮应注入任务态上下文卡片");
        assertTrue(payload.contains("plan: plan: read and summarize"), "应携带上一轮计划信息");
        assertTrue(secondRound.stream().anyMatch(message ->
                        message.role() == AiChatMessage.Role.ASSISTANT
                                && message.content() != null
                                && message.content().contains("[Task State]")),
                "任务态卡片应以 ASSISTANT 语义注入");
        assertFalse(secondRound.stream().anyMatch(message ->
                        message.role() == AiChatMessage.Role.SYSTEM
                                && message.content() != null
                                && message.content().contains("[Task State]")),
                "任务态卡片不应以 SYSTEM 角色注入");
    }

    @Test
    void shouldRecordLastFailureIntoNextRoundWhenToolMissing() {
        RecordingScriptedRuntimeModel runtimeModel = new RecordingScriptedRuntimeModel(List.of(
                assistantToolCall("call_1", "missingTool", "{}", "plan: run missing tool"),
                assistant("done")
        ));
        TaskStateAgent agent = TaskStateAgent.builder()
                .aiChatModel(runtimeModel)
                .build();

        assertEquals("done", agent.execute("run", "conv-task-state-failure"));
        List<AiChatMessage> secondRound = runtimeModel.requests().get(1).messages();
        String payload = secondRound.toString();
        assertTrue(payload.contains("[Task State]"), "失败后下一轮仍应带任务态上下文");
        assertTrue(payload.contains("lastFailure: tool=missingTool; reason=Tool not found: missingTool"),
                "缺失工具失败原因应写入 lastFailure");
        assertFalse(secondRound.stream().anyMatch(message ->
                        message.role() == AiChatMessage.Role.SYSTEM
                                && message.content() != null
                                && message.content().contains("lastFailure:")),
                "失败信息不应以 SYSTEM 角色注入");
    }

    @Test
    void shouldApplyCustomTaskStateBudgetConfiguredOnExecutorBuilder() {
        RecordingScriptedRuntimeModel runtimeModel = new RecordingScriptedRuntimeModel(List.of(
                assistantToolCall("call_1", "missingTool", "{}", "plan: " + "p".repeat(120)),
                assistant("done")
        ));
        TaskStateAgent agent = TaskStateAgent.builder()
                .aiChatModel(runtimeModel)
                .taskStatePlanMaxChars(20)
                .taskStateLastFailureMaxChars(32)
                .build();

        assertEquals("done", agent.execute("run", "conv-task-state-budget-custom"));
        List<AiChatMessage> secondRound = runtimeModel.requests().get(1).messages();
        String payload = secondRound.toString();
        assertTrue(payload.contains("plan: plan: pppppppppppppp"), "自定义 plan 预算应生效");
        assertTrue(payload.contains("lastFailure: tool=missingTool; reason=Tool..."),
                "自定义 lastFailure 预算应生效");
    }

    private static AiChatResponse assistant(String text) {
        return new AiChatResponse(AiChatMessage.assistant(text), null, null, null, null, null);
    }

    private static AiChatResponse assistantToolCall(String id, String name, String arguments, String content) {
        return new AiChatResponse(
                AiChatMessage.assistant(content, List.of(new AiToolCall(id, name, arguments))),
                null, null, null, null, null
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

    static class TaskStateAgent extends AbstractAgentExecutor<TaskStateAgent.Builder> {

        static class Builder extends AbstractAgentExecutor.Builder<Builder> {
            TaskStateAgent build() {
                this.name("task_state_agent")
                        .description("task state integration test agent")
                        .singleParameter("input")
                        .systemMessage("you are a test agent");
                return new TaskStateAgent(this);
            }
        }

        static Builder builder() {
            return new Builder();
        }

        TaskStateAgent(Builder builder) {
            super(builder);
        }
    }

    static class FileReadTool {
        @com.openmanus.aiframework.tool.AiTool
        public String readFile(@com.openmanus.aiframework.tool.AiParam("path") String path) {
            return "read:" + path;
        }
    }
}
