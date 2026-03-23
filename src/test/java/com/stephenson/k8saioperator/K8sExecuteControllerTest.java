package com.stephenson.k8saioperator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stephenson.k8saioperator.controller.K8sExecuteController;
import com.stephenson.k8saioperator.metrics.CloudWatchMetricsEmitter;
import com.stephenson.k8saioperator.model.ParsedCommand;
import com.stephenson.k8saioperator.service.AuditService;
import com.stephenson.k8saioperator.service.CommandParser;
import com.stephenson.k8saioperator.service.K8sClientAdapter;
import com.stephenson.k8saioperator.service.VerbGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Validates the HTTP layer of K8sExecuteController:
 * - 400 shape for forbidden verbs
 * - 200 shape for allowed verbs
 */
@WebMvcTest(K8sExecuteController.class)
class K8sExecuteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CommandParser commandParser;

    @MockBean
    private VerbGuard verbGuard;

    @MockBean
    private K8sClientAdapter k8sClientAdapter;

    @MockBean
    private AuditService auditService;

    @MockBean
    private CloudWatchMetricsEmitter metricsEmitter;

    @Test
    void forbiddenVerb_returns400WithAllowedFalse() throws Exception {
        ParsedCommand deleteCommand = ParsedCommand.builder()
                .verb("delete")
                .resource("pods")
                .namespace("production")
                .build();

        when(commandParser.parse(anyString())).thenReturn(deleteCommand);
        when(verbGuard.isAllowed("delete")).thenReturn(false);

        String requestBody = """
                {
                  "request_id": "req-001",
                  "user_prompt": "delete all pods in production"
                }
                """;

        mockMvc.perform(post("/k8s/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.request_id").value("req-001"))
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.reason").value("Verb 'delete' is not permitted"));
    }

    @Test
    void allowedVerb_returns200WithCommandAndResult() throws Exception {
        ParsedCommand getCommand = ParsedCommand.builder()
                .verb("get")
                .resource("pods")
                .namespace("production")
                .build();

        when(commandParser.parse(anyString())).thenReturn(getCommand);
        when(verbGuard.isAllowed("get")).thenReturn(true);
        when(k8sClientAdapter.execute(any(ParsedCommand.class))).thenReturn("NAME  READY  STATUS\napp-pod  1/1  Running");

        String requestBody = """
                {
                  "request_id": "req-002",
                  "user_prompt": "show me the pods in namespace production"
                }
                """;

        mockMvc.perform(post("/k8s/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request_id").value("req-002"))
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.command.verb").value("get"))
                .andExpect(jsonPath("$.command.resource").value("pods"))
                .andExpect(jsonPath("$.command.namespace").value("production"))
                .andExpect(jsonPath("$.result").exists());
    }

    @Test
    void execVerb_returns400() throws Exception {
        ParsedCommand execCommand = ParsedCommand.builder()
                .verb("exec")
                .resource("pods")
                .namespace("default")
                .build();

        when(commandParser.parse(anyString())).thenReturn(execCommand);
        when(verbGuard.isAllowed("exec")).thenReturn(false);

        String requestBody = """
                {
                  "request_id": "req-003",
                  "user_prompt": "exec into the pod"
                }
                """;

        mockMvc.perform(post("/k8s/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.allowed").value(false));
    }
}

