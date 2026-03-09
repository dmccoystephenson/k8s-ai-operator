package com.stephenson.k8saioperator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stephenson.k8saioperator.model.ParsedCommand;
import com.stephenson.k8saioperator.service.BedrockCommandParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Mocks Bedrock responses and asserts the parsed command structure.
 */
@ExtendWith(MockitoExtension.class)
class BedrockCommandParserTest {

    @Mock
    private BedrockRuntimeClient bedrockRuntimeClient;

    private BedrockCommandParser parser;

    @BeforeEach
    void setUp() {
        parser = new BedrockCommandParser(
                bedrockRuntimeClient,
                new ObjectMapper(),
                "anthropic.claude-3-sonnet-20240229-v1:0",
                512
        );
    }

    @Test
    void parsesGetPodsCommand() {
        String mockResponse = bedrockResponse("{\"verb\":\"get\",\"resource\":\"pods\",\"namespace\":\"production\"}");
        when(bedrockRuntimeClient.invokeModel(any(InvokeModelRequest.class)))
                .thenReturn(invokeModelResponse(mockResponse));

        ParsedCommand command = parser.parse("show me the pods in production");

        assertEquals("get", command.getVerb());
        assertEquals("pods", command.getResource());
        assertEquals("production", command.getNamespace());
    }

    @Test
    void parsesApplyCommand() {
        String mockResponse = bedrockResponse("{\"verb\":\"apply\",\"resource\":\"deployments\",\"namespace\":\"staging\"}");
        when(bedrockRuntimeClient.invokeModel(any(InvokeModelRequest.class)))
                .thenReturn(invokeModelResponse(mockResponse));

        ParsedCommand command = parser.parse("apply the deployment to staging");

        assertEquals("apply", command.getVerb());
        assertEquals("deployments", command.getResource());
        assertEquals("staging", command.getNamespace());
    }

    @Test
    void parsesDeleteCommand_modelMayReturnIt_verbGuardBlocksLater() {
        // Bedrock may return a delete verb — the guard (not the parser) rejects it
        String mockResponse = bedrockResponse("{\"verb\":\"delete\",\"resource\":\"pods\",\"namespace\":\"default\"}");
        when(bedrockRuntimeClient.invokeModel(any(InvokeModelRequest.class)))
                .thenReturn(invokeModelResponse(mockResponse));

        ParsedCommand command = parser.parse("remove all pods");

        assertEquals("delete", command.getVerb());
        // Enforcement happens in VerbGuard, not here
    }

    @Test
    void throwsOnMultiCommandArrayResponse() {
        String mockResponse = bedrockResponse("[{\"verb\":\"get\",\"resource\":\"pods\",\"namespace\":\"default\"},{\"verb\":\"apply\",\"resource\":\"deployments\",\"namespace\":\"staging\"}]");
        when(bedrockRuntimeClient.invokeModel(any(InvokeModelRequest.class)))
                .thenReturn(invokeModelResponse(mockResponse));

        assertThrows(IllegalStateException.class, () -> parser.parse("do two things"));
    }

    @Test
    void throwsOnEmptyContentArray() {
        String mockResponse = "{\"id\":\"msg_001\",\"content\":[],\"stop_reason\":\"end_turn\"}";
        when(bedrockRuntimeClient.invokeModel(any(InvokeModelRequest.class)))
                .thenReturn(invokeModelResponse(mockResponse));

        assertThrows(IllegalStateException.class, () -> parser.parse("anything"));
    }

    // --- Helpers ---

    private String bedrockResponse(String commandJson) {
        return String.format(
                "{\"id\":\"msg_001\",\"type\":\"message\",\"role\":\"assistant\"," +
                "\"content\":[{\"type\":\"text\",\"text\":\"%s\"}]," +
                "\"stop_reason\":\"end_turn\"}",
                commandJson.replace("\"", "\\\"")
        );
    }

    private InvokeModelResponse invokeModelResponse(String body) {
        return InvokeModelResponse.builder()
                .body(SdkBytes.fromUtf8String(body))
                .build();
    }
}

