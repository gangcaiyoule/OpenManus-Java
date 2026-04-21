package com.openmanus.infra.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.domain.model.WorkflowErrorCodes;
import com.openmanus.domain.model.WorkflowResponse;
import com.openmanus.domain.service.AgentService;
import com.openmanus.domain.service.SessionSandboxManager;
import com.openmanus.domain.service.WorkflowExecutionPort;
import com.openmanus.domain.service.WorkflowExecutionEventPort;
import com.openmanus.domain.service.WorkflowStreamService;
import com.openmanus.domain.service.WorkflowStreamPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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

    @Test
    void shouldReturnTrimmedConversationIdForBlankMessageWhenStateful() throws Exception {
        WorkflowStreamService service = createService(Runnable::run);
        MockMvc mockMvc = buildMockMvc(service);

        MvcResult asyncResult = mockMvc.perform(post("/api/agent/chat?stateful=true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "message", "   ",
                                "conversationId", "  conv-1  "
                        ))))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(WorkflowErrorCodes.INPUT_INVALID))
                .andExpect(jsonPath("$.conversationId").value("conv-1"));
    }

    @Test
    void shouldNotEchoInvalidConversationIdForBlankMessageWhenStateful() throws Exception {
        WorkflowStreamService service = createService(Runnable::run);
        MockMvc mockMvc = buildMockMvc(service);

        MvcResult asyncResult = mockMvc.perform(post("/api/agent/chat?stateful=true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "message", "   ",
                                "conversationId", "bad/id"
                        ))))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(WorkflowErrorCodes.INPUT_INVALID))
                .andExpect(jsonPath("$.conversationId").doesNotExist());
    }

    @Test
    void shouldRejectExplicitInvalidConversationIdWhenStateful() throws Exception {
        WorkflowStreamService service = createService(Runnable::run);
        AgentService agentService = mock(AgentService.class);
        MockMvc mockMvc = buildMockMvc(agentService, service);

        MvcResult asyncResult = mockMvc.perform(post("/api/agent/chat?stateful=true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "message", "hello",
                                "conversationId", "bad/id"
                        ))))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("conversationId非法"))
                .andExpect(jsonPath("$.errorCode").value(WorkflowErrorCodes.INPUT_INVALID))
                .andExpect(jsonPath("$.conversationId").doesNotExist());
    }

    @Test
    void shouldReturn400ForBlankExplicitConversationIdWhenStateful() throws Exception {
        WorkflowStreamService service = createService(Runnable::run);
        AgentService agentService = mock(AgentService.class);
        MockMvc mockMvc = buildMockMvc(agentService, service);

        MvcResult asyncResult = mockMvc.perform(post("/api/agent/chat?stateful=true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "message", "hello",
                                "conversationId", "   "
                        ))))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("conversationId非法"))
                .andExpect(jsonPath("$.errorCode").value(WorkflowErrorCodes.INPUT_INVALID));
    }

    @Test
    void shouldReturn500ForSyncChatServiceErrorPayload() throws Exception {
        WorkflowStreamService service = createService(Runnable::run);
        AgentService agentService = mock(AgentService.class);
        when(agentService.chat("task", "conv-1", true)).thenReturn(CompletableFuture.completedFuture(Map.of(
                "error", "boom",
                "conversationId", "conv-1"
        )));
        MockMvc mockMvc = buildMockMvc(agentService, service);

        MvcResult asyncResult = mockMvc.perform(post("/api/agent/chat?stateful=true&sync=true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "message", "task",
                                "conversationId", "conv-1"
                        ))))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("boom"))
                .andExpect(jsonPath("$.errorCode").value(WorkflowErrorCodes.INTERNAL_ERROR))
                .andExpect(jsonPath("$.conversationId").value("conv-1"));

        verify(agentService).chat("task", "conv-1", true);
    }

    @Test
    void shouldReturn500ForAsyncChatServiceErrorPayload() throws Exception {
        WorkflowStreamService service = createService(Runnable::run);
        AgentService agentService = mock(AgentService.class);
        when(agentService.chat("task", "conv-2", false)).thenReturn(CompletableFuture.completedFuture(Map.of(
                "error", "async boom",
                "errorCode", WorkflowErrorCodes.INTERNAL_ERROR,
                "conversationId", "conv-2"
        )));
        MockMvc mockMvc = buildMockMvc(agentService, service);

        MvcResult asyncResult = mockMvc.perform(post("/api/agent/chat?stateful=true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "message", "task",
                                "conversationId", "conv-2"
                        ))))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("async boom"))
                .andExpect(jsonPath("$.errorCode").value(WorkflowErrorCodes.INTERNAL_ERROR))
                .andExpect(jsonPath("$.conversationId").value("conv-2"));

        verify(agentService).chat("task", "conv-2", false);
    }

    @Test
    void shouldReturn500WhenChatFutureCompletesExceptionally() throws Exception {
        WorkflowStreamService service = createService(Runnable::run);
        AgentService agentService = mock(AgentService.class);
        when(agentService.chat("task", "conv-3", false))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("boom")));
        MockMvc mockMvc = buildMockMvc(agentService, service);

        MvcResult asyncResult = mockMvc.perform(post("/api/agent/chat?stateful=true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "message", "task",
                                "conversationId", "conv-3"
                        ))))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("内部错误，请稍后重试"))
                .andExpect(jsonPath("$.errorCode").value(WorkflowErrorCodes.INTERNAL_ERROR))
                .andExpect(jsonPath("$.conversationId").value("conv-3"));
    }

    private MockMvc buildMockMvc(WorkflowStreamService service) {
        return buildMockMvc(mock(AgentService.class), service);
    }

    private MockMvc buildMockMvc(AgentService agentService, WorkflowStreamService service) {
        AgentController controller = new AgentController(
                agentService,
                service,
                mock(SessionSandboxManager.class)
        );
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    private WorkflowStreamService createService(Executor executor) {
        WorkflowExecutionPort workflow = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        WorkflowStreamPublisher streamPublisher = mock(WorkflowStreamPublisher.class);
        return new WorkflowStreamService(workflow, executionEventPort, streamPublisher, executor) {
            @Override
            protected long postExecutionDrainDelayMs() {
                return 0L;
            }
        };
    }

    private WorkflowStreamService createFallbackMappingServiceReturningUnknownErrorCode() {
        WorkflowExecutionPort workflow = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        WorkflowStreamPublisher streamPublisher = mock(WorkflowStreamPublisher.class);
        Executor directExecutor = Runnable::run;
        return new WorkflowStreamService(workflow, executionEventPort, streamPublisher, directExecutor) {
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
        WorkflowExecutionPort workflow = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        doThrow(new StacklessIllegalStateException("tracker unavailable"))
                .when(executionEventPort).addListener(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        WorkflowStreamPublisher streamPublisher = mock(WorkflowStreamPublisher.class);
        Executor directExecutor = Runnable::run;
        return new WorkflowStreamService(workflow, executionEventPort, streamPublisher, directExecutor) {
            @Override
            protected long postExecutionDrainDelayMs() {
                return 0L;
            }
        };
    }

    private WorkflowStreamService createServiceReturningConflictingErrorCodeAndMessage() {
        WorkflowExecutionPort workflow = mock(WorkflowExecutionPort.class);
        WorkflowExecutionEventPort executionEventPort = mock(WorkflowExecutionEventPort.class);
        WorkflowStreamPublisher streamPublisher = mock(WorkflowStreamPublisher.class);
        Executor directExecutor = Runnable::run;
        return new WorkflowStreamService(workflow, executionEventPort, streamPublisher, directExecutor) {
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
