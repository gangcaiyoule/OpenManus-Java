package com.openmanus.domain.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.agent.workflow.UnifiedWorkflow;
import com.openmanus.domain.model.WorkflowErrorCodes;
import com.openmanus.domain.model.WorkflowResponse;
import com.openmanus.domain.service.AgentService;
import com.openmanus.domain.service.SessionSandboxManager;
import com.openmanus.domain.service.WorkflowStreamService;
import com.openmanus.infra.monitoring.AgentExecutionTracker;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentControllerServiceContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReturn400ForBlankInputWithRealServiceFlow() throws Exception {
        WorkflowStreamService service = createService(Runnable::run);
        MockMvc mockMvc = buildMockMvc(service);

        mockMvc.perform(post("/api/agent/workflow-stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Payload("   "))))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("X-OpenManus-Deprecated-Endpoint"))
                .andExpect(jsonPath("$.errorCode").value(WorkflowErrorCodes.INPUT_INVALID));
    }

    @Test
    void shouldReturn503ForOverloadWithRealServiceFlow() throws Exception {
        Executor rejectingExecutor = command -> {
            throw new StacklessRejectedExecutionException("queue full");
        };
        WorkflowStreamService service = createService(rejectingExecutor);
        MockMvc mockMvc = buildMockMvc(service);

        mockMvc.perform(post("/api/agent/workflow-stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Payload("task"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().doesNotExist("X-OpenManus-Deprecated-Endpoint"))
                .andExpect(jsonPath("$.errorCode").value(WorkflowErrorCodes.ASYNC_SUBMIT_REJECTED));
    }

    @Test
    void shouldReturn503ForRuntimeExceptionWithRealServiceFlow() throws Exception {
        Executor brokenExecutor = command -> {
            throw new StacklessIllegalStateException("executor unavailable");
        };
        WorkflowStreamService service = createService(brokenExecutor);
        MockMvc mockMvc = buildMockMvc(service);

        mockMvc.perform(post("/api/agent/workflow-stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Payload("task"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().doesNotExist("X-OpenManus-Deprecated-Endpoint"))
                .andExpect(jsonPath("$.errorCode").value(WorkflowErrorCodes.ASYNC_SUBMIT_EXCEPTION));
    }

    @Test
    void shouldReturn500OnUnifiedPathForUnknownErrorCodeFallbackMapping() throws Exception {
        WorkflowStreamService service = createFallbackMappingServiceReturningUnknownErrorCode();
        MockMvc mockMvc = buildMockMvc(service);

        mockMvc.perform(post("/api/agent/workflow-stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Payload("task"))))
                .andExpect(status().isInternalServerError())
                .andExpect(header().doesNotExist("X-OpenManus-Deprecated-Endpoint"))
                .andExpect(jsonPath("$.errorCode").value(WorkflowErrorCodes.UNKNOWN_ERROR));
    }

    @Test
    void shouldPrioritizeErrorCodeOverErrorMessageWhenBothPresent() throws Exception {
        WorkflowStreamService service = createServiceReturningConflictingErrorCodeAndMessage();
        MockMvc mockMvc = buildMockMvc(service);

        mockMvc.perform(post("/api/agent/workflow-stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Payload("task"))))
                .andExpect(status().isInternalServerError())
                .andExpect(header().doesNotExist("X-OpenManus-Deprecated-Endpoint"))
                .andExpect(jsonPath("$.errorCode").value(WorkflowErrorCodes.INTERNAL_ERROR));
    }

    @Test
    void shouldReturn500ForInternalErrorWithRealServiceFlow() throws Exception {
        WorkflowStreamService service = createServiceWithListenerRegistrationFailure();
        MockMvc mockMvc = buildMockMvc(service);

        mockMvc.perform(post("/api/agent/workflow-stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Payload("task"))))
                .andExpect(status().isInternalServerError())
                .andExpect(header().doesNotExist("X-OpenManus-Deprecated-Endpoint"))
                .andExpect(jsonPath("$.errorCode").value(WorkflowErrorCodes.INTERNAL_ERROR));
    }

    @Test
    void shouldRejectLegacyStreamEndpointAsNotFound() throws Exception {
        WorkflowStreamService service = createService(Runnable::run);
        MockMvc mockMvc = buildMockMvc(service);

        mockMvc.perform(post("/api/agent/think-do-reflect-stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Payload("task"))))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("X-OpenManus-Deprecated-Endpoint"));
    }

    @Test
    void shouldReturn400ForBlankMessageOnChatEndpoint() throws Exception {
        WorkflowStreamService service = createService(Runnable::run);
        MockMvc mockMvc = buildMockMvc(service);

        MvcResult asyncResult = mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("message", "   "))))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(WorkflowErrorCodes.INPUT_INVALID));
    }

    private MockMvc buildMockMvc(WorkflowStreamService service) {
        AgentController controller = new AgentController(
                mock(AgentService.class),
                service,
                mock(SessionSandboxManager.class)
        );
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    private WorkflowStreamService createService(Executor executor) {
        UnifiedWorkflow workflow = mock(UnifiedWorkflow.class);
        AgentExecutionTracker tracker = mock(AgentExecutionTracker.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        return new WorkflowStreamService(workflow, tracker, messagingTemplate, executor) {
            @Override
            protected long postExecutionDrainDelayMs() {
                return 0L;
            }
        };
    }

    private WorkflowStreamService createFallbackMappingServiceReturningUnknownErrorCode() {
        UnifiedWorkflow workflow = mock(UnifiedWorkflow.class);
        AgentExecutionTracker tracker = mock(AgentExecutionTracker.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        Executor directExecutor = Runnable::run;
        return new WorkflowStreamService(workflow, tracker, messagingTemplate, directExecutor) {
            @Override
            public WorkflowResponse executeWorkflowAndStreamEvents(String userInput) {
                return WorkflowResponse.builder()
                        .success(false)
                        .error("unknown")
                        .errorCode(WorkflowErrorCodes.UNKNOWN_ERROR)
                        .build();
            }

            @Override
            protected long postExecutionDrainDelayMs() {
                return 0L;
            }
        };
    }

    private WorkflowStreamService createServiceWithListenerRegistrationFailure() {
        UnifiedWorkflow workflow = mock(UnifiedWorkflow.class);
        AgentExecutionTracker tracker = mock(AgentExecutionTracker.class);
        doThrow(new StacklessIllegalStateException("tracker unavailable"))
                .when(tracker).addListener(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        Executor directExecutor = Runnable::run;
        return new WorkflowStreamService(workflow, tracker, messagingTemplate, directExecutor) {
            @Override
            protected long postExecutionDrainDelayMs() {
                return 0L;
            }
        };
    }

    private WorkflowStreamService createServiceReturningConflictingErrorCodeAndMessage() {
        UnifiedWorkflow workflow = mock(UnifiedWorkflow.class);
        AgentExecutionTracker tracker = mock(AgentExecutionTracker.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        Executor directExecutor = Runnable::run;
        return new WorkflowStreamService(workflow, tracker, messagingTemplate, directExecutor) {
            @Override
            public WorkflowResponse executeWorkflowAndStreamEvents(String userInput) {
                return WorkflowResponse.builder()
                        .success(false)
                        .error("输入不能为空")
                        .errorCode(WorkflowErrorCodes.INTERNAL_ERROR)
                        .build();
            }

            @Override
            protected long postExecutionDrainDelayMs() {
                return 0L;
            }
        };
    }

    private record Payload(String input) {
    }

    private static final class StacklessRejectedExecutionException extends RejectedExecutionException {
        private StacklessRejectedExecutionException(String message) {
            super(message);
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

    private static final class StacklessIllegalStateException extends IllegalStateException {
        private StacklessIllegalStateException(String message) {
            super(message);
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }
}
