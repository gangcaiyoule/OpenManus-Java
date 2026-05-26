package com.openmanus.agentteam.infra;

import com.openmanus.agent.coordination.AgentCoordinator;
import com.openmanus.agentteam.application.AgentTeamRole;
import com.openmanus.agentteam.application.AgentTeamPromptProvider;
import com.openmanus.agentteam.application.AgentTeamToolPolicy;
import com.openmanus.agentteam.application.SubAgentToolPolicy;
import com.openmanus.agentteam.application.TeamMasterToolPolicy;
import com.openmanus.aiframework.runtime.AiChatModel;
import com.openmanus.aiframework.runtime.AiMemoryProvider;
import com.openmanus.aiframework.runtime.AiSessionSandboxGateway;
import com.openmanus.aiframework.tool.AiRegisteredTool;
import com.openmanus.domain.service.ExecutionEventPort;
import com.openmanus.infra.config.LocalAgentToolRegistry;
import com.openmanus.infra.config.OpenManusProperties;

import java.util.List;
import java.util.Objects;

/**
 * Builds role-scoped coordinators for the agentteam module.
 */
public class AgentTeamCoordinatorFactory {

    private final AiChatModel aiChatModel;
    private final AiMemoryProvider aiMemoryProvider;
    private final AiSessionSandboxGateway sessionSandboxGateway;
    private final OpenManusProperties properties;
    private final ExecutionEventPort executionEventPort;
    private final LocalAgentToolRegistry localAgentToolRegistry;
    private final AgentTeamPromptProvider promptProvider;
    private final TeamMasterToolPolicy teamMasterToolPolicy;
    private final SubAgentToolPolicy subAgentToolPolicy;

    public AgentTeamCoordinatorFactory(
            AiChatModel aiChatModel,
            AiMemoryProvider aiMemoryProvider,
            AiSessionSandboxGateway sessionSandboxGateway,
            OpenManusProperties properties,
            ExecutionEventPort executionEventPort,
            LocalAgentToolRegistry localAgentToolRegistry,
            AgentTeamPromptProvider promptProvider,
            TeamMasterToolPolicy teamMasterToolPolicy,
            SubAgentToolPolicy subAgentToolPolicy
    ) {
        this.aiChatModel = Objects.requireNonNull(aiChatModel, "aiChatModel");
        this.aiMemoryProvider = Objects.requireNonNull(aiMemoryProvider, "aiMemoryProvider");
        this.sessionSandboxGateway = Objects.requireNonNull(sessionSandboxGateway, "sessionSandboxGateway");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.executionEventPort = executionEventPort;
        this.localAgentToolRegistry = Objects.requireNonNull(localAgentToolRegistry, "localAgentToolRegistry");
        this.promptProvider = Objects.requireNonNull(promptProvider, "promptProvider");
        this.teamMasterToolPolicy = Objects.requireNonNull(teamMasterToolPolicy, "teamMasterToolPolicy");
        this.subAgentToolPolicy = Objects.requireNonNull(subAgentToolPolicy, "subAgentToolPolicy");
    }

    public AgentCoordinator create(AgentTeamRole role) {
        AgentCoordinator.Builder builder = AgentCoordinator.builder()
                .aiChatModel(aiChatModel)
                .aiMemoryProvider(aiMemoryProvider)
                .sessionSandboxGateway(sessionSandboxGateway)
                .maxIterations(properties.getChatMemory().getReactMaxIterations())
                .maxExecutionSeconds(properties.getChatMemory().getReactMaxExecutionSeconds())
                .repeatedToolCallThreshold(properties.getChatMemory().getReactRepeatedToolCallThreshold())
                .taskStatePlanMaxChars(properties.getChatMemory().getTaskStatePlanMaxChars())
                .taskStateInProgressMaxChars(properties.getChatMemory().getTaskStateInProgressMaxChars())
                .taskStateLastFailureMaxChars(properties.getChatMemory().getTaskStateLastFailureMaxChars())
                .taskStateTodoMaxItems(properties.getChatMemory().getTaskStateTodoMaxItems())
                .taskStateTodoItemMaxChars(properties.getChatMemory().getTaskStateTodoItemMaxChars())
                .enableToolResultBudget(properties.getChatMemory().isToolResultBudgetEnabled())
                .toolResultBudgetMinChars(properties.getChatMemory().getToolResultBudgetMinChars())
                .toolResultBudgetPreviewHeadChars(properties.getChatMemory().getToolResultBudgetPreviewHeadChars())
                .toolResultBudgetPreviewTailChars(properties.getChatMemory().getToolResultBudgetPreviewTailChars())
                .toolResultBudgetDecayChars(properties.getChatMemory().getToolResultBudgetDecayChars())
                .executionEventPort(executionEventPort)
                .name("agentteam_" + role.name().toLowerCase())
                .description("Role-scoped executor for agentteam role " + role.name().toLowerCase())
                .singleParameter("Role-scoped request")
                .systemMessage(systemPromptFor(role));

        for (AiRegisteredTool tool : toolsFor(role)) {
            builder.tool(tool);
        }
        return builder.build();
    }

    private String systemPromptFor(AgentTeamRole role) {
        return switch (role) {
            case TEAM_MASTER -> promptProvider.teamMasterSystemPromptTemplate();
            case SUB_AGENT -> promptProvider.subAgentSystemPromptTemplate();
        };
    }

    private List<AiRegisteredTool> toolsFor(AgentTeamRole role) {
        List<AiRegisteredTool> defaultTools = localAgentToolRegistry.allLocalTools();
        AgentTeamToolPolicy policy = switch (role) {
            case TEAM_MASTER -> teamMasterToolPolicy;
            case SUB_AGENT -> subAgentToolPolicy;
        };
        return policy.selectTools(defaultTools);
    }
}
