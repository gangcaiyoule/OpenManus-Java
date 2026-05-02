package com.openmanus.infra.web;

import com.openmanus.domain.model.AgentExecutionEvent;
import com.openmanus.domain.model.DetailedExecutionFlow;
import com.openmanus.domain.service.ExecutionMonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent 执行监控控制器
 * 专注于 Agent 执行流程的可视化监控
 */
@RestController
@RequestMapping("/api/agent-monitoring")
@Tag(name = "Agent执行监控", description = "Agent执行流程可视化监控API")
@Slf4j
public class AgentMonitoringController {

    private final ExecutionMonitoringService executionMonitoringService;

    public AgentMonitoringController(ExecutionMonitoringService executionMonitoringService) {
        this.executionMonitoringService = executionMonitoringService;
    }

    /**
     * 获取监控仪表板数据
     */
    @GetMapping("/dashboard")
    @Operation(summary = "获取监控仪表板", description = "获取Agent执行监控的仪表板数据")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        try {
            Map<String, Object> dashboard = new HashMap<>();
            
            // 活跃会话统计
            Map<String, AgentExecutionEvent> activeSessions = executionMonitoringService.getAllActiveSessions();
            dashboard.put("totalActiveSessions", activeSessions.size());
            
            // 详细执行流程统计
            Map<String, DetailedExecutionFlow> detailedFlows = executionMonitoringService.getAllDetailedExecutionFlows();
            List<DetailedExecutionFlow> validFlows = sanitizeFlows(detailedFlows);
            dashboard.put("totalDetailedFlows", validFlows.size());
            
            // 运行中的执行流程
            long runningFlows = validFlows.stream()
                    .filter(flow -> flow.getStatus() == DetailedExecutionFlow.FlowStatus.RUNNING)
                    .count();
            dashboard.put("runningFlows", runningFlows);
            
            // 已完成的执行流程
            long completedFlows = validFlows.stream()
                    .filter(flow -> flow.getStatus() == DetailedExecutionFlow.FlowStatus.COMPLETED)
                    .count();
            dashboard.put("completedFlows", completedFlows);
            
            // 失败的执行流程
            long failedFlows = validFlows.stream()
                    .filter(flow -> flow.getStatus() == DetailedExecutionFlow.FlowStatus.FAILED)
                    .count();
            dashboard.put("failedFlows", failedFlows);
            
            return ResponseEntity.ok(dashboard);
        } catch (Exception e) {
            log.error("Error getting dashboard data", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取所有活跃的Agent会话
     */
    @GetMapping("/sessions/active")
    @Operation(summary = "获取活跃会话", description = "获取当前所有活跃的Agent执行会话")
    public ResponseEntity<Map<String, AgentExecutionEvent>> getActiveSessions() {
        try {
            Map<String, AgentExecutionEvent> activeSessions = executionMonitoringService.getAllActiveSessions();
            return ResponseEntity.ok(activeSessions);
        } catch (Exception e) {
            log.error("Error getting active sessions", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取会话的执行事件
     */
    @GetMapping("/sessions/{sessionId}/events")
    @Operation(summary = "获取会话事件", description = "获取指定会话的所有执行事件")
    public ResponseEntity<List<AgentExecutionEvent>> getSessionEvents(@PathVariable String sessionId) {
        try {
            List<AgentExecutionEvent> events = executionMonitoringService.getSessionEvents(sessionId);
            return ResponseEntity.ok(events);
        } catch (Exception e) {
            log.error("Error getting session events for sessionId: {}", sessionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取详细执行流程
     */
    @GetMapping("/sessions/{sessionId}/detailed-flow")
    @Operation(summary = "获取详细执行流程", description = "获取指定会话的详细执行流程，包括思考、执行、反思等各个阶段")
    public ResponseEntity<DetailedExecutionFlow> getDetailedExecutionFlow(@PathVariable String sessionId) {
        try {
            DetailedExecutionFlow flow = executionMonitoringService.getDetailedExecutionFlow(sessionId);
            if (flow == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(flow);
        } catch (Exception e) {
            log.error("Error getting detailed execution flow for sessionId: {}", sessionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取所有详细执行流程
     */
    @GetMapping("/flows/all")
    @Operation(summary = "获取所有执行流程", description = "获取所有的详细执行流程")
    public ResponseEntity<Map<String, DetailedExecutionFlow>> getAllDetailedFlows() {
        try {
            Map<String, DetailedExecutionFlow> flows = executionMonitoringService.getAllDetailedExecutionFlows();
            return ResponseEntity.ok(flows);
        } catch (Exception e) {
            log.error("Error getting all detailed flows", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取最近的执行流程
     */
    @GetMapping("/flows/recent")
    @Operation(summary = "获取最近执行流程", description = "获取最近的执行流程，按开始时间倒序")
    public ResponseEntity<List<DetailedExecutionFlow>> getRecentFlows(@RequestParam(defaultValue = "10") int limit) {
        if (limit <= 0) {
            return ResponseEntity.badRequest().build();
        }
        try {
            List<DetailedExecutionFlow> recentFlows = sanitizeFlows(executionMonitoringService.getAllDetailedExecutionFlows())
                    .stream()
                    .sorted(Comparator.comparing(
                            DetailedExecutionFlow::getStartTime,
                            Comparator.nullsFirst(LocalDateTime::compareTo)
                    ).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(recentFlows);
        } catch (Exception e) {
            log.error("Error getting recent flows", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取会话统计信息
     */
    @GetMapping("/sessions/{sessionId}/stats")
    @Operation(summary = "获取会话统计", description = "获取指定会话的统计信息")
    public ResponseEntity<Map<String, Object>> getSessionStats(@PathVariable String sessionId) {
        try {
            Map<String, Object> stats = executionMonitoringService.getSessionStatistics(sessionId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting session stats for sessionId: {}", sessionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 清理已完成的执行流程
     */
    @DeleteMapping("/flows/cleanup")
    @Operation(summary = "清理执行流程", description = "清理指定时间之前的已完成执行流程")
    public ResponseEntity<Map<String, Object>> cleanupFlows(@RequestParam(defaultValue = "24") int maxAgeHours) {
        if (maxAgeHours <= 0) {
            return ResponseEntity.badRequest().build();
        }
        try {
            executionMonitoringService.cleanupCompletedFlows(maxAgeHours);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "清理完成，删除了 " + maxAgeHours + " 小时前的已完成流程");
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error cleaning up flows", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private static List<DetailedExecutionFlow> sanitizeFlows(Map<String, DetailedExecutionFlow> flows) {
        if (flows == null || flows.isEmpty()) {
            return List.of();
        }
        return flows.values().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
