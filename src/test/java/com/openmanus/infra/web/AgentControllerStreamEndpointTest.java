package com.openmanus.infra.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.domain.model.WorkflowErrorCodes;
import com.openmanus.domain.model.WorkflowResponse;
import com.openmanus.domain.service.AgentService;
import com.openmanus.domain.service.SessionSandboxManager;
import com.openmanus.domain.service.WorkflowStreamService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentControllerStreamEndpointTest {

    private static final String DEPRECATION_HEADER = "X-OpenManus-Deprecated-Endpoint";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldAcceptUnifiedStreamEndpoint() throws Exception {
        WorkflowStreamService workflowStreamService = mock(WorkflowStreamService.class);
        WorkflowResponse response = WorkflowResponse.builder()
                .success(true)
                .sessionId("session-1")
                .build();
        when(workflowStreamService.executeWorkflowAndStreamEvents(anyString())).thenReturn(response);

        MockMvc mockMvc = buildMockMvc(workflowStreamService);
        mockMvc.perform(post("/api/agent/workflow-stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Payload("task"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-1"))
                .andExpect(jsonPath("$.topic").value("/topic/executions/session-1"))
                .andExpect(header().doesNotExist(DEPRECATION_HEADER));
    }

    @Test
    void shouldRejectLegacyStreamEndpoint() throws Exception {
        WorkflowStreamService workflowStreamService = mock(WorkflowStreamService.class);
        MockMvc mockMvc = buildMockMvc(workflowStreamService);

        mockMvc.perform(post("/api/agent/think-do-reflect-stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Payload("task"))))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist(DEPRECATION_HEADER));
    }

    @Test
    void shouldReturn400ForUnifiedStreamEndpointWhenServiceFailsInputValidation() throws Exception {
        WorkflowStreamService workflowStreamService = mock(WorkflowStreamService.class);
        WorkflowResponse response = WorkflowResponse.builder()
                .success(false)
                .error("输入不能为空")
                .errorCode(WorkflowErrorCodes.INPUT_INVALID)
                .build();
        when(workflowStreamService.executeWorkflowAndStreamEvents(anyString())).thenReturn(response);

        MockMvc mockMvc = buildMockMvc(workflowStreamService);
        mockMvc.perform(post("/api/agent/workflow-stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Payload("task"))))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist(DEPRECATION_HEADER));
    }

    @Test
    void shouldReturn500WhenUnifiedStreamEndpointSucceedsWithoutSessionId() throws Exception {
        WorkflowStreamService workflowStreamService = mock(WorkflowStreamService.class);
        WorkflowResponse response = WorkflowResponse.builder()
                .success(true)
                .build();
        when(workflowStreamService.executeWorkflowAndStreamEvents(anyString())).thenReturn(response);

        MockMvc mockMvc = buildMockMvc(workflowStreamService);
        mockMvc.perform(post("/api/agent/workflow-stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Payload("task"))))
                .andExpect(status().isInternalServerError())
                .andExpect(header().doesNotExist(DEPRECATION_HEADER))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(WorkflowErrorCodes.INTERNAL_ERROR))
                .andExpect(jsonPath("$.error").value("无法启动工作流：未生成会话ID"));
    }

    @Test
    void shouldReturn503ForUnifiedStreamEndpointWhenServiceOverloaded() throws Exception {
        WorkflowStreamService workflowStreamService = mock(WorkflowStreamService.class);
        WorkflowResponse response = WorkflowResponse.builder()
                .success(false)
                .error("任务提交失败，请稍后重试")
                .errorCode(WorkflowErrorCodes.ASYNC_SUBMIT_REJECTED)
                .build();
        when(workflowStreamService.executeWorkflowAndStreamEvents(anyString())).thenReturn(response);

        MockMvc mockMvc = buildMockMvc(workflowStreamService);
        mockMvc.perform(post("/api/agent/workflow-stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Payload("task"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().doesNotExist(DEPRECATION_HEADER));
    }

    @Test
    void shouldReturn500ForUnifiedStreamEndpointWhenServiceReturnsUnknownError() throws Exception {
        WorkflowStreamService workflowStreamService = mock(WorkflowStreamService.class);
        WorkflowResponse response = WorkflowResponse.builder()
                .success(false)
                .error("unknown")
                .build();
        when(workflowStreamService.executeWorkflowAndStreamEvents(anyString())).thenReturn(response);

        MockMvc mockMvc = buildMockMvc(workflowStreamService);
        mockMvc.perform(post("/api/agent/workflow-stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Payload("task"))))
                .andExpect(status().isInternalServerError())
                .andExpect(header().doesNotExist(DEPRECATION_HEADER));
    }

    private MockMvc buildMockMvc(WorkflowStreamService workflowStreamService) {
        AgentController controller = new AgentController(
                mock(AgentService.class),
                workflowStreamService,
                mock(SessionSandboxManager.class)
        );
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    private record Payload(String input) {
    }
}
