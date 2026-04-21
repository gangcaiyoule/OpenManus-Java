package com.openmanus.agent.tool;

import com.openmanus.aiframework.tool.AiRegisteredTool;
import com.openmanus.aiframework.tool.AiToolExecutionRequest;
import com.openmanus.aiframework.tool.AiToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectionToolTest {

    @Test
    void shouldKeepTaskHistoryIsolatedByMemoryId() {
        ReflectionTool tool = new ReflectionTool();

        assertEquals(
                "任务执行记录已保存，可进行后续反思分析",
                tool.recordTask("task-1", "desc-a", "step-a", "tool-a", "result-a", "session-a")
        );
        assertEquals(
                "任务执行记录已保存，可进行后续反思分析",
                tool.recordTask("task-1", "desc-b", "step-b", "tool-b", "result-b", "session-b")
        );

        String sessionAReflection = tool.reflectOnTask("task-1", "session-a");
        String sessionBReflection = tool.reflectOnTask("task-1", "session-b");

        assertTrue(sessionAReflection.contains("描述: desc-a"));
        assertTrue(sessionAReflection.contains("结果: result-a"));
        assertTrue(sessionBReflection.contains("描述: desc-b"));
        assertTrue(sessionBReflection.contains("结果: result-b"));
        assertEquals("未找到任务记录: task-1", tool.reflectOnTask("task-1", "session-c"));
    }

    @Test
    void shouldTreatBlankMemoryIdAsDefaultBucket() {
        ReflectionTool tool = new ReflectionTool();

        tool.recordTask("task-1", "desc", "step", "tool", "result", "   ");

        assertTrue(tool.reflectOnTask("task-1", null).contains("描述: desc"));
        assertTrue(tool.getTaskHistory("").contains("ID: task-1"));
    }

    @Test
    void shouldFormatNullResultWithoutThrowing() {
        ReflectionTool tool = new ReflectionTool();

        tool.recordTask("task-1", "desc", "step", "tool", null, "session-a");

        String reflection = tool.reflectOnTask("task-1", "session-a");
        String history = tool.getTaskHistory("session-a");

        assertTrue(reflection.contains("- 结果: "));
        assertFalse(reflection.contains("null"));
        assertTrue(history.contains("结果: "));
        assertFalse(history.contains("结果: null"));
    }

    @Test
    void aiToolRegistryShouldHideMemoryIdFromToolSchemaAndPassScopedExecution() {
        ReflectionTool tool = new ReflectionTool();
        Map<String, AiRegisteredTool> tools = AiToolRegistry.scan(tool).stream()
                .collect(Collectors.toMap(AiRegisteredTool::name, registeredTool -> registeredTool));

        AiRegisteredTool recordTask = tools.get("recordTask");
        AiRegisteredTool reflectOnTask = tools.get("reflectOnTask");
        AiRegisteredTool getTaskHistory = tools.get("getTaskHistory");

        String schemaJson = recordTask.parameters().schema().toString();
        assertFalse(schemaJson.contains("memoryId"));

        String recordResult = recordTask.executor().execute(
                new AiToolExecutionRequest(
                        "call-1",
                        "recordTask",
                        """
                        {
                          "taskId": "task-1",
                          "taskDescription": "desc",
                          "steps": "step",
                          "toolsUsed": "tool",
                          "result": "result"
                        }
                        """
                ),
                "session-a"
        );

        String reflection = reflectOnTask.executor().execute(
                new AiToolExecutionRequest("call-2", "reflectOnTask", "{\"taskId\":\"task-1\"}"),
                "session-a"
        );
        String missingReflection = reflectOnTask.executor().execute(
                new AiToolExecutionRequest("call-3", "reflectOnTask", "{\"taskId\":\"task-1\"}"),
                "session-b"
        );
        String history = getTaskHistory.executor().execute(
                new AiToolExecutionRequest("call-4", "getTaskHistory", "{}"),
                "session-a"
        );

        assertEquals("任务执行记录已保存，可进行后续反思分析", recordResult);
        assertTrue(reflection.contains("描述: desc"));
        assertEquals("未找到任务记录: task-1", missingReflection);
        assertTrue(history.contains("ID: task-1"));
    }
}
