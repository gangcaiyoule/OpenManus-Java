package com.openmanus.infra.web;

import com.openmanus.domain.model.SessionSandboxInfo;
import com.openmanus.domain.model.WorkflowErrorCodes;
import com.openmanus.domain.service.AgentService;
import com.openmanus.domain.service.SessionSandboxManager;
import com.openmanus.domain.service.WorkflowStreamService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentControllerSessionInfoTest {

    @Test
    void shouldReturnSessionIdOnlyWhenSandboxInfoIsMissing() throws Exception {
        SessionSandboxManager sandboxManager = mock(SessionSandboxManager.class);
        when(sandboxManager.getSandboxInfo("session-missing")).thenReturn(Optional.empty());

        MockMvc mockMvc = buildMockMvc(sandboxManager);

        mockMvc.perform(get("/api/agent/session/session-missing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-missing"))
                .andExpect(jsonPath("$.sandboxVncUrl").doesNotExist())
                .andExpect(jsonPath("$.sandboxStatus").doesNotExist());
    }

    @Test
    void shouldReturnSandboxInfoWhenSessionSandboxExists() throws Exception {
        SessionSandboxManager sandboxManager = mock(SessionSandboxManager.class);
        when(sandboxManager.getSandboxInfo("session-1")).thenReturn(Optional.of(
                SessionSandboxInfo.builder()
                        .sessionId("session-1")
                        .vncUrl("http://localhost:6080")
                        .status(SessionSandboxInfo.SandboxStatus.RUNNING)
                        .createdAt(LocalDateTime.of(2026, 4, 6, 11, 0))
                        .build()
        ));

        MockMvc mockMvc = buildMockMvc(sandboxManager);

        mockMvc.perform(get("/api/agent/session/session-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-1"))
                .andExpect(jsonPath("$.sandboxVncUrl").value("http://localhost:6080"))
                .andExpect(jsonPath("$.sandboxStatus").value("RUNNING"))
                .andExpect(jsonPath("$.sandboxAvailable").value(true));
    }

    @Test
    void shouldTrimWhitespaceFromSessionIdBeforeQueryingSandboxInfo() {
        SessionSandboxManager sandboxManager = mock(SessionSandboxManager.class);
        when(sandboxManager.getSandboxInfo("session-1")).thenReturn(Optional.empty());

        AgentController controller = buildController(sandboxManager);
        ResponseEntity<Map<String, Object>> response = controller.getSessionInfo("  session-1  ");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("session-1", response.getBody().get("sessionId"));
        assertFalse(response.getBody().containsKey("sandboxStatus"));
        verify(sandboxManager).getSandboxInfo("session-1");
    }

    @Test
    void shouldRejectInvalidSessionIdOnSessionInfoEndpoint() throws Exception {
        SessionSandboxManager sandboxManager = mock(SessionSandboxManager.class);
        MockMvc mockMvc = buildMockMvc(sandboxManager);

        mockMvc.perform(get("/api/agent/session/session%2Bid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("sessionId非法"))
                .andExpect(jsonPath("$.errorCode").value(WorkflowErrorCodes.INPUT_INVALID))
                .andExpect(jsonPath("$.sessionId").doesNotExist());

        verify(sandboxManager, never()).getSandboxInfo(org.mockito.ArgumentMatchers.anyString());
    }

    private MockMvc buildMockMvc(SessionSandboxManager sandboxManager) {
        return MockMvcBuilders.standaloneSetup(buildController(sandboxManager)).build();
    }

    private AgentController buildController(SessionSandboxManager sandboxManager) {
        return new AgentController(
                mock(AgentService.class),
                mock(WorkflowStreamService.class),
                sandboxManager
        );
    }
}
