package com.openmanus.agentteam.application;

import com.openmanus.agentteam.domain.model.SubTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SubAgentExecutionIsolation Tests")
class SubAgentExecutionIsolationTest {

    @Test
    @DisplayName("should execute subtask through subagent role port")
    void shouldExecuteSubTaskThroughSubAgentRolePort() {
        RecordingRoleExecutionPort roleExecutionPort = new RecordingRoleExecutionPort("done");
        AgentTeamPromptProvider promptProvider = new AgentTeamPromptProvider() {
            @Override
            public String taskDecompositionPromptTemplate() {
                return "unused";
            }

            @Override
            public String teamMasterSystemPromptTemplate() {
                return "unused";
            }

            @Override
            public String subAgentSystemPromptTemplate() {
                return "unused";
            }

            @Override
            public String subAgentExecutionPromptTemplate() {
                return "task={{taskDescription}}";
            }
        };
        SubAgentExecutionService service = new SubAgentExecutionService(roleExecutionPort, promptProvider);
        SubTask subTask = new SubTask("task-1", "group-1", "API", "Collect API requirements", System.currentTimeMillis());

        SubTaskExecutionOutput output = service.execute(subTask, "agent-1");

        assertThat(output.summary()).isEqualTo("done");
        assertThat(output.detail()).isEqualTo("done");
        assertThat(roleExecutionPort.role).isEqualTo(AgentTeamRole.SUB_AGENT);
        assertThat(roleExecutionPort.input).isEqualTo("task=Collect API requirements");
        assertThat(roleExecutionPort.conversationId).isEqualTo("task-1");
    }

    private static final class RecordingRoleExecutionPort implements AgentTeamRoleExecutionPort {

        private final String result;
        private AgentTeamRole role;
        private String input;
        private String conversationId;

        private RecordingRoleExecutionPort(String result) {
            this.result = result;
        }

        @Override
        public String executeSync(AgentTeamRole role, String input, String conversationId) {
            this.role = role;
            this.input = input;
            this.conversationId = conversationId;
            return result;
        }
    }
}
