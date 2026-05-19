package com.openmanus.agentteam.domain.model;

/**
 * Candidate subtask emitted by decomposition.
 */
public record SubTaskPlan(String title, String description) {

    public SubTaskPlan {
        title = title == null ? "" : title.trim();
        description = description == null ? "" : description.trim();
    }
}
