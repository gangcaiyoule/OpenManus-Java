package com.openmanus.agentteam.application;

import com.openmanus.agentteam.domain.model.SubTask;
import com.openmanus.domain.service.AgentExecutionPort;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Executes one subtask by reusing the existing single-agent execution path.
 */
@Slf4j
public class SubAgentExecutionService {

    private final AgentExecutionPort agentExecutionPort;
    private final AgentTeamPromptProvider promptProvider;

    public SubAgentExecutionService(
            AgentExecutionPort agentExecutionPort,
            AgentTeamPromptProvider promptProvider
    ) {
        this.agentExecutionPort = agentExecutionPort;
        this.promptProvider = promptProvider;
    }

    public SubTaskExecutionOutput execute(SubTask subTask, String agentId) {
        String prompt = buildSubTaskPrompt(subTask, agentId);
        log.info(
                "SubAgentExecution dispatching to single-agent runtime: agentId={}, groupId={}, taskId={}, title={}",
                agentId,
                subTask.getGroupId(),
                subTask.getTaskId(),
                subTask.getTitle()
        );
        String result = agentExecutionPort.executeSync(prompt, subTask.getTaskId());
        String summary = summarize(result);
        log.info(
                "SubAgentExecution finished runtime call: agentId={}, groupId={}, taskId={}, title={}, summary={}",
                agentId,
                subTask.getGroupId(),
                subTask.getTaskId(),
                subTask.getTitle(),
                summary
        );
        return new SubTaskExecutionOutput(summary, result);
    }

    private String buildSubTaskPrompt(SubTask subTask, String agentId) {
        return PromptTemplateRenderer.render(
                promptProvider.subAgentExecutionPromptTemplate(),
                Map.of(
                        "agentId", safe(agentId),
                        "taskId", safe(subTask.getTaskId()),
                        "groupId", safe(subTask.getGroupId()),
                        "taskTitle", safe(subTask.getTitle()),
                        "taskDescription", safe(subTask.getDescription())
                )
        );
    }

    private String summarize(String result) {
        if (result == null || result.isBlank()) {
            return "Subtask finished but returned no usable content";
        }
        String compact = result.trim();
        return compact.length() > 80 ? compact.substring(0, 80) : compact;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
