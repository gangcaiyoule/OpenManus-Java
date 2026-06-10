package com.openmanus.agentteam.domain.model;

import lombok.Getter;

/**
 * Runtime subtask tracked by the agent-team scheduler.
 */
@Getter
public class SubTask {

    private final String taskId;
    private final String groupId;
    private final String title;
    private final String description;
    private final long createdAt;
    private TaskStatus status;
    private String assignedAgentId;
    private String resultSummary;
    private String resultDetail;
    private String errorMessage;
    private long claimedAt;
    private long startedAt;
    private long finishedAt;

    public SubTask(String taskId, String groupId, String title, String description, long createdAt) {
        this.taskId = taskId;
        this.groupId = groupId;
        this.title = title == null ? "" : title.trim();
        this.description = description == null ? "" : description.trim();
        this.createdAt = createdAt;
        this.status = TaskStatus.PENDING;
    }

    public void claim(String agentId, long timestamp) {
        this.assignedAgentId = agentId;
        this.claimedAt = timestamp;
        this.status = TaskStatus.CLAIMED;
    }

    public void markRunning(long timestamp) {
        this.startedAt = timestamp;
        this.status = TaskStatus.RUNNING;
    }

    public void markSucceeded(String summary, String detail, long timestamp) {
        this.resultSummary = summary;
        this.resultDetail = detail;
        this.errorMessage = null;
        this.finishedAt = timestamp;
        this.status = TaskStatus.SUCCEEDED;
    }

    public void markFailed(String error, long timestamp) {
        this.errorMessage = error;
        this.finishedAt = timestamp;
        this.status = TaskStatus.FAILED;
    }
}
