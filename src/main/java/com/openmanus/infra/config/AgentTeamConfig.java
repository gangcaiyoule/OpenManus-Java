package com.openmanus.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.agentteam.application.AgentTeamApplicationService;
import com.openmanus.agentteam.application.AgentTeamPromptProvider;
import com.openmanus.agentteam.application.MasterAgentOrchestrator;
import com.openmanus.agentteam.application.SubAgentExecutionService;
import com.openmanus.agentteam.application.TaskDecompositionService;
import com.openmanus.agentteam.domain.port.AgentMessageBusPort;
import com.openmanus.agentteam.domain.port.TaskGroupRepositoryPort;
import com.openmanus.agentteam.domain.port.TaskPoolPort;
import com.openmanus.agentteam.domain.service.DefaultResultAggregationService;
import com.openmanus.agentteam.domain.service.DefaultTaskGroupManager;
import com.openmanus.agentteam.domain.service.ResultAggregationService;
import com.openmanus.agentteam.domain.service.TaskGroupManager;
import com.openmanus.agentteam.domain.service.TaskGroupStatusCalculator;
import com.openmanus.agentteam.infra.ClasspathAgentTeamPromptProvider;
import com.openmanus.agentteam.infra.InMemoryAgentMessageBus;
import com.openmanus.agentteam.infra.InMemoryTaskGroupRepository;
import com.openmanus.agentteam.infra.InMemoryTaskPool;
import com.openmanus.agentteam.infra.SubAgentWorkerManager;
import com.openmanus.aiframework.runtime.AiChatModel;
import com.openmanus.domain.service.AgentExecutionPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bean wiring for the agentteam module.
 */
@Configuration
@EnableConfigurationProperties(AgentTeamProperties.class)
public class AgentTeamConfig {

    @Bean
    TaskGroupStatusCalculator taskGroupStatusCalculator() {
        return new TaskGroupStatusCalculator();
    }

    @Bean
    TaskGroupRepositoryPort taskGroupRepositoryPort() {
        return new InMemoryTaskGroupRepository();
    }

    @Bean
    TaskPoolPort taskPoolPort(TaskGroupRepositoryPort repositoryPort) {
        return new InMemoryTaskPool(repositoryPort);
    }

    @Bean
    AgentMessageBusPort agentMessageBusPort() {
        return new InMemoryAgentMessageBus();
    }

    @Bean
    TaskGroupManager taskGroupManager(
            TaskGroupRepositoryPort repositoryPort,
            TaskGroupStatusCalculator statusCalculator
    ) {
        return new DefaultTaskGroupManager(repositoryPort, statusCalculator);
    }

    @Bean
    ResultAggregationService resultAggregationService() {
        return new DefaultResultAggregationService();
    }

    @Bean
    AgentTeamPromptProvider agentTeamPromptProvider() {
        return new ClasspathAgentTeamPromptProvider();
    }

    @Bean
    TaskDecompositionService taskDecompositionService(
            AiChatModel aiChatModel,
            ObjectMapper objectMapper,
            AgentTeamPromptProvider promptProvider
    ) {
        return new TaskDecompositionService(aiChatModel, objectMapper, promptProvider);
    }

    @Bean
    SubAgentExecutionService subAgentExecutionService(
            AgentExecutionPort agentExecutionPort,
            AgentTeamPromptProvider promptProvider
    ) {
        return new SubAgentExecutionService(agentExecutionPort, promptProvider);
    }

    @Bean(destroyMethod = "close")
    SubAgentWorkerManager subAgentWorkerManager(
            AgentTeamProperties properties,
            TaskPoolPort taskPoolPort,
            AgentMessageBusPort messageBusPort,
            SubAgentExecutionService executionService
    ) {
        return new SubAgentWorkerManager(
                properties.getWorkerCount(),
                properties.getIdlePollIntervalMillis(),
                taskPoolPort,
                messageBusPort,
                executionService
        );
    }

    @Bean
    MasterAgentOrchestrator masterAgentOrchestrator(
            AgentExecutionPort agentExecutionPort,
            TaskDecompositionService taskDecompositionService,
            TaskGroupManager taskGroupManager,
            TaskPoolPort taskPoolPort,
            ResultAggregationService resultAggregationService,
            SubAgentWorkerManager subAgentWorkerManager,
            AgentTeamProperties properties
    ) {
        return new MasterAgentOrchestrator(
                agentExecutionPort,
                taskDecompositionService,
                taskGroupManager,
                taskPoolPort,
                resultAggregationService,
                subAgentWorkerManager,
                properties.getMasterPollIntervalMillis(),
                properties.getMaxSubTasksPerGroup()
        );
    }

    @Bean
    AgentTeamApplicationService agentTeamApplicationService(MasterAgentOrchestrator masterAgentOrchestrator) {
        return new AgentTeamApplicationService(masterAgentOrchestrator);
    }
}
