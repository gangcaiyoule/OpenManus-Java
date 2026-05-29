package com.openmanus.agentteam.application;

import com.openmanus.agent.coordination.AgentCoordinator;
import com.openmanus.agentteam.infra.AgentTeamCoordinatorFactory;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * Executes one request with the coordinator built for a specific agentteam role.
 */
public class AgentTeamRoleExecutionService implements AgentTeamRoleExecutionPort {

    private final AgentTeamCoordinatorFactory coordinatorFactory;

    public AgentTeamRoleExecutionService(AgentTeamCoordinatorFactory coordinatorFactory) {
        this.coordinatorFactory = coordinatorFactory;
    }

    @Override
    public String executeSync(AgentTeamRole role, String input, String memoryId) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("input cannot be null or blank");
        }
        Object runtimeMemoryId = memoryId != null && !memoryId.isBlank()
                ? memoryId
                : UUID.randomUUID();
        try (MDC.MDCCloseable ignored = MDC.putCloseable("sessionId", String.valueOf(runtimeMemoryId))) {
            AgentCoordinator coordinator = coordinatorFactory.create(role);
            return coordinator.execute(input, runtimeMemoryId);
        }
    }
}
