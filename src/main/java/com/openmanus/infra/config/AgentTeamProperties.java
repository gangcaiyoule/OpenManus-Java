package com.openmanus.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Runtime configuration for the agentteam module.
 */
@Data
@ConfigurationProperties(prefix = "openmanus.agentteam")
public class AgentTeamProperties {

    private boolean enabled = false;
    private int workerCount = 3;
    private long idlePollIntervalMillis = 500L;
    private long masterPollIntervalMillis = 300L;
    private int maxSubTasksPerGroup = 5;
}
