package com.openmanus.smoke.tool;

import com.openmanus.agent.tool.TaskReflectionTool;
import com.openmanus.smoke.SmokeTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for TaskReflectionTool.
 * Verifies task recording and reflection functionality works correctly.
 */
@Tag("smoke")
@DisplayName("TaskReflectionTool Smoke Tests")
class TaskReflectionToolSmokeTest implements SmokeTest {

    private TaskReflectionTool taskReflectionTool;

    @BeforeEach
    void setUp() {
        taskReflectionTool = new TaskReflectionTool();
    }

    @Nested
    @DisplayName("recordTask")
    class RecordTaskTests {

        @Test
        @DisplayName("should record task successfully")
        void recordTask_withValidInput_recordsSuccessfully() {
            // Given
            String taskId = "task-001";
            String taskDescription = "Test task description";
            String steps = "Step 1: Start\nStep 2: Execute\nStep 3: Complete";
            String toolsUsed = "PythonExecutionTool, ShellTool";
            String result = "Task completed successfully";

            // When
            String resultMessage = taskReflectionTool.recordTask(
                    taskId, taskDescription, steps, toolsUsed, result, "memory-1");

            // Then
            assertThat(resultMessage).contains("记录已保存");
        }

        @Test
        @DisplayName("should handle null memoryId")
        void recordTask_withNullMemoryId_handlesGracefully() {
            // Given
            String taskId = "task-null-memory";

            // When
            String resultMessage = taskReflectionTool.recordTask(
                    taskId, "desc", "steps", "tools", "result", null);

            // Then
            assertThat(resultMessage).contains("记录已保存");
        }

        @Test
        @DisplayName("should handle blank memoryId")
        void recordTask_withBlankMemoryId_handlesGracefully() {
            // Given
            String taskId = "task-blank-memory";

            // When
            String resultMessage = taskReflectionTool.recordTask(
                    taskId, "desc", "steps", "tools", "result", "   ");

            // Then
            assertThat(resultMessage).contains("记录已保存");
        }

        @Test
        @DisplayName("should update existing task record")
        void recordTask_withExistingTaskId_updatesRecord() {
            // Given
            String taskId = "task-update";

            // When
            taskReflectionTool.recordTask(taskId, "First desc", "steps", "tools", "first result", "mem-1");
            String resultMessage = taskReflectionTool.recordTask(
                    taskId, "Second desc", "steps", "tools", "second result", "mem-1");

            // Then
            assertThat(resultMessage).contains("记录已保存");
        }
    }

    @Nested
    @DisplayName("reflectOnTask")
    class ReflectOnTaskTests {

        @Test
        @DisplayName("should generate reflection for recorded task")
        void reflectOnTask_withRecordedTask_generatesReflection() {
            // Given
            String taskId = "task-reflect-001";
            taskReflectionTool.recordTask(taskId, "Reflection test task",
                    "1. Start\n2. Execute", "PythonExecutionTool", "Success", "mem-reflect");

            // When
            String reflection = taskReflectionTool.reflectOnTask(taskId, "mem-reflect");

            // Then
            assertThat(reflection).contains("任务反思分析");
            assertThat(reflection).contains(taskId);
            assertThat(reflection).contains("Reflection test task");
            assertThat(reflection).contains("PythonExecutionTool");
        }

        @Test
        @DisplayName("should return error for non-existent task")
        void reflectOnTask_withNonExistentTask_returnsError() {
            // Given
            String taskId = "non-existent-task";

            // When
            String reflection = taskReflectionTool.reflectOnTask(taskId, "mem-1");

            // Then
            assertThat(reflection).contains("未找到任务记录");
        }

        @Test
        @DisplayName("should handle null memoryId in reflection")
        void reflectOnTask_withNullMemoryId_handlesCorrectly() {
            // Given
            String taskId = "task-null-reflect";
            taskReflectionTool.recordTask(taskId, "Desc", "Steps", "Tools", "Result", null);

            // When
            String reflection = taskReflectionTool.reflectOnTask(taskId, null);

            // Then
            assertThat(reflection).contains("任务反思分析");
        }
    }

    @Nested
    @DisplayName("getTaskHistory")
    class GetTaskHistoryTests {

        @Test
        @DisplayName("should return task history with multiple records")
        void getTaskHistory_withMultipleTasks_returnsAllTasks() {
            // Given
            taskReflectionTool.recordTask("task-hist-1", "First task",
                    "steps1", "tool1", "result1", "mem-history");
            taskReflectionTool.recordTask("task-hist-2", "Second task",
                    "steps2", "tool2", "result2", "mem-history");

            // When
            String history = taskReflectionTool.getTaskHistory("mem-history");

            // Then
            assertThat(history).contains("任务历史记录");
            assertThat(history).contains("task-hist-1");
            assertThat(history).contains("task-hist-2");
        }

        @Test
        @DisplayName("should return empty message for no history")
        void getTaskHistory_withNoHistory_returnsEmptyMessage() {
            // When
            String history = taskReflectionTool.getTaskHistory("mem-empty");

            // Then
            assertThat(history).contains("暂无任务历史记录");
        }

        @Test
        @DisplayName("should isolate history by memoryId")
        void getTaskHistory_withDifferentMemoryIds_isolatesHistory() {
            // Given
            taskReflectionTool.recordTask("task-a", "Task A", "steps", "tools", "result", "mem-a");
            taskReflectionTool.recordTask("task-b", "Task B", "steps", "tools", "result", "mem-b");

            // When
            String historyA = taskReflectionTool.getTaskHistory("mem-a");
            String historyB = taskReflectionTool.getTaskHistory("mem-b");

            // Then
            assertThat(historyA).contains("Task A");
            assertThat(historyA).doesNotContain("Task B");
            assertThat(historyB).contains("Task B");
            assertThat(historyB).doesNotContain("Task A");
        }

        @Test
        @DisplayName("should handle null memoryId")
        void getTaskHistory_withNullMemoryId_handlesGracefully() {
            // Given
            taskReflectionTool.recordTask("task-null", "Task", "steps", "tools", "result", null);

            // When
            String history = taskReflectionTool.getTaskHistory(null);

            // Then
            assertThat(history).contains("task-null");
        }

        @Test
        @DisplayName("should truncate long results in history")
        void getTaskHistory_withLongResult_truncatesResult() {
            // Given
            String longResult = "A".repeat(200); // Beyond MAX_RESULT_PREVIEW_LENGTH
            taskReflectionTool.recordTask("task-long", "Long task",
                    "steps", "tools", longResult, "mem-long");

            // When
            String history = taskReflectionTool.getTaskHistory("mem-long");

            // Then
            assertThat(history).contains("task-long");
            assertThat(history).contains("..."); // Truncation indicator
        }
    }

    @Nested
    @DisplayName("Memory Isolation")
    class MemoryIsolationTests {

        @Test
        @DisplayName("should isolate task records by memory bucket")
        void memoryIsolation_recordsAreIsolatedByMemoryId() {
            // Given
            taskReflectionTool.recordTask("shared-id", "Memory A task",
                    "steps", "tool", "result", "memory-a");
            taskReflectionTool.recordTask("shared-id", "Memory B task",
                    "steps", "tool", "result", "memory-b");

            // When
            String historyA = taskReflectionTool.getTaskHistory("memory-a");
            String historyB = taskReflectionTool.getTaskHistory("memory-b");

            // Then
            assertThat(historyA).contains("Memory A task");
            assertThat(historyA).doesNotContain("Memory B task");
            assertThat(historyB).contains("Memory B task");
            assertThat(historyB).doesNotContain("Memory A task");
        }

        @Test
        @DisplayName("should use default bucket for null memoryId")
        void memoryIsolation_usesDefaultBucketForNullMemoryId() {
            // Given
            taskReflectionTool.recordTask("task-default", "Default bucket task",
                    "steps", "tool", "result", null);

            // When
            String history = taskReflectionTool.getTaskHistory(null);

            // Then
            assertThat(history).contains("Default bucket task");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle empty strings in task fields")
        void edgeCases_handlesEmptyStrings() {
            // Given
            String taskId = "task-empty";

            // When
            String result = taskReflectionTool.recordTask(taskId, "", "", "", "", "mem-empty");

            // Then
            assertThat(result).contains("记录已保存");
        }

        @Test
        @DisplayName("should handle null strings in task fields")
        void edgeCases_handlesNullStrings() {
            // Given
            String taskId = "task-null-fields";

            // When
            String result = taskReflectionTool.recordTask(taskId, null, null, null, null, "mem-null");

            // Then
            assertThat(result).contains("记录已保存");
        }
    }
}
