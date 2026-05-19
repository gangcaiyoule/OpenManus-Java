package com.openmanus.agentteam.application;

import com.openmanus.agentteam.domain.model.DecompositionPlan;
import com.openmanus.agentteam.domain.model.SubTask;
import com.openmanus.agentteam.domain.model.SubTaskPlan;
import com.openmanus.agentteam.domain.model.TaskGroup;
import com.openmanus.agentteam.domain.model.TaskGroupResult;
import com.openmanus.agentteam.domain.model.TaskGroupSnapshot;
import com.openmanus.agentteam.domain.service.ResultAggregationService;
import com.openmanus.agentteam.domain.service.TaskGroupManager;
import com.openmanus.agentteam.domain.port.TaskPoolPort;
import com.openmanus.domain.service.AgentExecutionPort;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Main orchestration entry for the V1 agent-team flow.
 */
@Slf4j
public class MasterAgentOrchestrator {

    private final AgentExecutionPort agentExecutionPort;
    private final TaskDecompositionService decompositionService;
    private final TaskGroupManager taskGroupManager;
    private final TaskPoolPort taskPoolPort;
    private final ResultAggregationService resultAggregationService;
    private final com.openmanus.agentteam.infra.SubAgentWorkerManager workerManager;
    private final long masterPollIntervalMillis;
    private final int maxSubTasksPerGroup;

    public MasterAgentOrchestrator(
            AgentExecutionPort agentExecutionPort,
            TaskDecompositionService decompositionService,
            TaskGroupManager taskGroupManager,
            TaskPoolPort taskPoolPort,
            ResultAggregationService resultAggregationService,
            com.openmanus.agentteam.infra.SubAgentWorkerManager workerManager,
            long masterPollIntervalMillis,
            int maxSubTasksPerGroup
    ) {
        this.agentExecutionPort = agentExecutionPort;
        this.decompositionService = decompositionService;
        this.taskGroupManager = taskGroupManager;
        this.taskPoolPort = taskPoolPort;
        this.resultAggregationService = resultAggregationService;
        this.workerManager = workerManager;
        this.masterPollIntervalMillis = masterPollIntervalMillis;
        this.maxSubTasksPerGroup = maxSubTasksPerGroup;
    }

    public String execute(String userInput, String conversationId) {
        DecompositionPlan plan = decompositionService.decompose(userInput, maxSubTasksPerGroup);
        log.info(
                "MasterAgent decomposition finished: parallelizable={}, subTaskCount={}, reason={}",
                plan.parallelizable(),
                plan.subTasks() == null ? 0 : plan.subTasks().size(),
                plan.reason()
        );
        if (!plan.parallelizable()) {
            log.info("MasterAgent falling back to single-agent execution: conversationId={}", conversationId);
            return agentExecutionPort.executeSync(userInput, conversationId);
        }

        workerManager.ensureStarted();
        TaskGroup taskGroup = taskGroupManager.createGroup(
                conversationId == null || conversationId.isBlank() ? UUID.randomUUID().toString() : conversationId,
                "master-agent",
                userInput
        );
        List<SubTask> subTasks = materializeSubTasks(taskGroup.getGroupId(), plan.subTasks());
        log.info(
                "MasterAgent created task group: groupId={}, conversationId={}, subTaskCount={}",
                taskGroup.getGroupId(),
                conversationId,
                subTasks.size()
        );
        taskGroupManager.registerSubTasks(taskGroup.getGroupId(), subTasks);
        for (SubTask subTask : subTasks) {
            log.info(
                    "MasterAgent submitting subtask to pool: groupId={}, taskId={}, title={}",
                    taskGroup.getGroupId(),
                    subTask.getTaskId(),
                    subTask.getTitle()
            );
            taskPoolPort.submit(subTask);
        }

        TaskGroupSnapshot snapshot = waitForCompletion(taskGroup.getGroupId());
        TaskGroupResult result = resultAggregationService.aggregate(
                taskGroup,
                taskPoolPort.findByGroupId(taskGroup.getGroupId())
        );
        log.info(
                "MasterAgent aggregation finished: groupId={}, status={}, successCount={}, failedCount={}",
                taskGroup.getGroupId(),
                snapshot.status(),
                snapshot.succeededTasks(),
                snapshot.failedTasks()
        );
        return renderFinalAnswer(taskGroup, snapshot, result);
    }

    private List<SubTask> materializeSubTasks(String groupId, List<SubTaskPlan> subTaskPlans) {
        List<SubTask> subTasks = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (SubTaskPlan plan : subTaskPlans) {
            subTasks.add(new SubTask(
                    UUID.randomUUID().toString(),
                    groupId,
                    plan.title(),
                    plan.description(),
                    now
            ));
        }
        return subTasks;
    }

    private TaskGroupSnapshot waitForCompletion(String groupId) {
        while (true) {
            TaskGroupSnapshot snapshot = taskGroupManager.getSnapshot(groupId);
            if (snapshot.allFinished()) {
                return snapshot;
            }
            try {
                Thread.sleep(masterPollIntervalMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("master polling interrupted", exception);
            }
        }
    }

    private String renderFinalAnswer(TaskGroup taskGroup, TaskGroupSnapshot snapshot, TaskGroupResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("AgentTeam execution finished.\n");
        builder.append("groupId: ").append(taskGroup.getGroupId()).append('\n');
        builder.append("status: ").append(snapshot.status()).append('\n');
        builder.append("success: ").append(snapshot.succeededTasks()).append('\n');
        builder.append("failed: ").append(snapshot.failedTasks()).append('\n');

        if (!result.successResults().isEmpty()) {
            builder.append("\nSuccessful subtasks:\n");
            for (var success : result.successResults()) {
                builder.append("- ").append(success.title()).append(": ")
                        .append(success.summary()).append('\n');
            }
        }
        if (!result.failures().isEmpty()) {
            builder.append("\nFailed subtasks:\n");
            for (var failure : result.failures()) {
                builder.append("- ").append(failure.title()).append(": ")
                        .append(failure.errorMessage()).append('\n');
            }
        }
        return builder.toString().trim();
    }
}
