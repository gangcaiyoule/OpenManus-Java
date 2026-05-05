package com.openmanus.agent.tool;

import com.openmanus.aiframework.tool.AiParam;
import com.openmanus.aiframework.tool.AiTool;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 反思工具 - 记录和分析任务执行过程
 * 
 * 功能：
 * 1. 记录任务执行历史
 * 2. 提供反思分析框架
 * 3. 查询历史记录
 * 
 * 采用 Record 模式简化数据对象
 */
@Slf4j
public class TaskReflectionTool {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_RESULT_PREVIEW_LENGTH = 100;
    private static final String DEFAULT_MEMORY_BUCKET = "__default__";

    // 按 memoryId 隔离任务执行历史记录，避免跨会话串扰
    private final Map<String, Map<String, TaskRecord>> taskHistoryByMemory = new ConcurrentHashMap<>();

    @AiTool("记录任务执行过程，用于后续反思")
    public String recordTask(@AiParam("任务ID") String taskId,
                           @AiParam("任务描述") String taskDescription,
                           @AiParam("执行步骤") String steps,
                           @AiParam("使用的工具") String toolsUsed,
                           @AiParam("执行结果") String result,
                           String memoryId) {
        try {
            TaskRecord record = new TaskRecord(taskId, taskDescription, steps, toolsUsed, result, LocalDateTime.now());
            tasksForMemory(memoryId).put(taskId, record);
            log.info("记录任务执行: memoryId={}, taskId={}", normalizeMemoryId(memoryId), taskId);
            return "任务执行记录已保存，可进行后续反思分析";
        } catch (Exception e) {
            log.error("记录任务失败", e);
            return "记录任务失败: " + e.getMessage();
        }
    }

    @AiTool("对指定任务进行反思分析")
    public String reflectOnTask(@AiParam("任务ID") String taskId, String memoryId) {
        try {
            TaskRecord record = tasksForMemory(memoryId).get(taskId);
            if (record == null) {
                return "未找到任务记录: " + taskId;
            }

            String reflection = """
                任务反思分析
                
                任务信息：
                - ID: %s
                - 描述: %s
                - 执行时间: %s
                
                执行过程：
                - 步骤: %s
                - 使用工具: %s
                - 结果: %s
                
                反思要点：
                1. 推理过程是否合理？
                2. 工具选择是否恰当？
                3. 执行效率如何？
                4. 结果质量如何？
                5. 有哪些改进空间？
                
                请基于以上信息进行深度反思。
                """.formatted(
                    record.taskId(),
                    record.taskDescription(),
                    record.executionTime().format(DATE_FORMATTER),
                    record.steps(),
                    record.toolsUsed(),
                    safeText(record.result())
                );

            log.info("生成任务反思: memoryId={}, taskId={}", normalizeMemoryId(memoryId), taskId);
            return reflection;
        } catch (Exception e) {
            log.error("任务反思失败", e);
            return "任务反思失败: " + e.getMessage();
        }
    }

    @AiTool("获取所有任务历史记录")
    public String getTaskHistory(String memoryId) {
        try {
            Map<String, TaskRecord> scopedHistory = tasksForMemory(memoryId);
            if (scopedHistory.isEmpty()) {
                return "暂无任务历史记录";
            }

            StringBuilder sb = new StringBuilder("任务历史记录\n\n");
            scopedHistory.values().stream()
                .sorted((a, b) -> b.executionTime().compareTo(a.executionTime()))
                .forEach(record -> sb.append(formatHistoryRecord(record)));

            return sb.toString();
        } catch (Exception e) {
            log.error("获取任务历史失败", e);
            return "获取任务历史失败: " + e.getMessage();
        }
    }
    
    /**
     * 格式化历史记录条目
     */
    private String formatHistoryRecord(TaskRecord record) {
        String result = safeText(record.result());
        String resultPreview = result.length() > MAX_RESULT_PREVIEW_LENGTH
            ? result.substring(0, MAX_RESULT_PREVIEW_LENGTH) + "..."
            : result;

        return """
            ID: %s
            描述: %s
            时间: %s
            结果: %s
            ---
            """.formatted(
                record.taskId(),
                record.taskDescription(),
                record.executionTime().format(DATE_FORMATTER),
                resultPreview
            );
    }

    private Map<String, TaskRecord> tasksForMemory(String memoryId) {
        return taskHistoryByMemory.computeIfAbsent(normalizeMemoryId(memoryId), ignored -> new ConcurrentHashMap<>());
    }

    private String normalizeMemoryId(String memoryId) {
        if (memoryId == null || memoryId.isBlank()) {
            return DEFAULT_MEMORY_BUCKET;
        }
        return memoryId.trim();
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }

    /**
     * 任务记录 - 使用 Record 简化不可变数据对象
     */
    record TaskRecord(
        String taskId,
        String taskDescription,
        String steps,
        String toolsUsed,
        String result,
        LocalDateTime executionTime
    ) {}
} 
