package com.stephenson.k8saioperator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stephenson.k8saioperator.model.ParsedCommand;
import com.stephenson.k8saioperator.service.AnthropicCommandParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Mocks HTTP responses from the Anthropic API and asserts the parsed command structure.
 */
@ExtendWith(MockitoExtension.class)
class AnthropicCommandParserTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private AnthropicCommandParser parser;

    @BeforeEach
    void setUp() {
        parser = new AnthropicCommandParser(
                httpClient,
                new ObjectMapper(),
                "test-api-key",
                "claude-sonnet-4-20250514",
                512
        );
    }

    @Test
    void parsesGetPodsCommand() throws Exception {
        String mockResponse = anthropicResponse("{\"verb\":\"get\",\"resource\":\"pods\",\"namespace\":\"production\"}");
        stubHttpResponse(200, mockResponse);

        ParsedCommand command = parser.parse("show me the pods in production");

        assertEquals("get", command.getVerb());
        assertEquals("pods", command.getResource());
        assertEquals("production", command.getNamespace());
    }

    @Test
    void parsesApplyCommand() throws Exception {
        String mockResponse = anthropicResponse("{\"verb\":\"apply\",\"resource\":\"deployments\",\"namespace\":\"staging\"}");
        stubHttpResponse(200, mockResponse);

        ParsedCommand command = parser.parse("apply the deployment to staging");

        assertEquals("apply", command.getVerb());
        assertEquals("deployments", command.getResource());
        assertEquals("staging", command.getNamespace());
    }

    @Test
    void parsesDeleteCommand_modelMayReturnIt_verbGuardBlocksLater() throws Exception {
        String mockResponse = anthropicResponse("{\"verb\":\"delete\",\"resource\":\"pods\",\"namespace\":\"default\"}");
        stubHttpResponse(200, mockResponse);

        ParsedCommand command = parser.parse("remove all pods");

        assertEquals("delete", command.getVerb());
    }

    @Test
    void throwsOnMultiCommandArrayResponse() throws Exception {
        String mockResponse = anthropicResponse("[{\"verb\":\"get\",\"resource\":\"pods\",\"namespace\":\"default\"},{\"verb\":\"apply\",\"resource\":\"deployments\",\"namespace\":\"staging\"}]");
        stubHttpResponse(200, mockResponse);

        assertThrows(IllegalStateException.class, () -> parser.parse("do two things"));
    }

    @Test
    void throwsOnEmptyContentArray() throws Exception {
        String mockResponse = "{\"id\":\"msg_001\",\"content\":[],\"stop_reason\":\"end_turn\"}";
        stubHttpResponse(200, mockResponse);

        assertThrows(IllegalStateException.class, () -> parser.parse("anything"));
    }

    @Test
    void throwsOnNon200StatusCode() throws Exception {
        when(httpResponse.statusCode()).thenReturn(401);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> parser.parse("anything"));
        assertTrue(ex.getMessage().contains("HTTP 401"));
    }

    @Test
    void throwsOnBlankApiKey() {
        assertThrows(IllegalArgumentException.class, () ->
                new AnthropicCommandParser(httpClient, new ObjectMapper(), "", "claude-sonnet-4-20250514", 512));
    }

    @Test
    void throwsOnNullApiKey() {
        assertThrows(IllegalArgumentException.class, () ->
                new AnthropicCommandParser(httpClient, new ObjectMapper(), null, "claude-sonnet-4-20250514", 512));
    }

    @Test
    void throwsOnIOException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("connection refused"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> parser.parse("anything"));
        assertTrue(ex.getMessage().contains("Failed to call Anthropic API"));
    }

    // --- Helpers ---

    @SuppressWarnings("unchecked")
    private void stubHttpResponse(int statusCode, String body) throws Exception {
        when(httpResponse.statusCode()).thenReturn(statusCode);
        when(httpResponse.body()).thenReturn(body);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
    }

    private String anthropicResponse(String commandJson) {
        return String.format(
                "{\"id\":\"msg_001\",\"type\":\"message\",\"role\":\"assistant\"," +
                "\"content\":[{\"type\":\"text\",\"text\":\"%s\"}]," +
                "\"stop_reason\":\"end_turn\"}",
                commandJson.replace("\"", "\\\"")
        );
    }
}
