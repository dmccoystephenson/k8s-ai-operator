package com.stephenson.k8saioperator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stephenson.k8saioperator.model.ParsedCommand;
import com.stephenson.k8saioperator.service.AnthropicCommandParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mocks HTTP responses from the Anthropic API and asserts the parsed command structure,
 * and captures the outgoing request to check the endpoint, headers, model, token cap and message shape.
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

    @Test
    void nonJsonModelText_failureMessageDoesNotRepeatTheModelText() throws Exception {
        // Models echo the prompt when they refuse, and this message reaches the logs and block_reason
        stubHttpResponse(200, anthropicResponse("Sorry, I cannot delete sentinel-prompt-echo for you"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> parser.parse("anything"));

        assertFalse(ex.getMessage().contains("sentinel-prompt-echo"), ex.getMessage());
        assertEquals("Failed to parse Anthropic response into a command: invalid JSON", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void twoTextBlocks_areRejectedAsMultipleCommands() throws Exception {
        stubHttpResponse(200, "{\"id\":\"msg_001\",\"content\":["
                + "{\"type\":\"text\",\"text\":\"{}\"},"
                + "{\"type\":\"text\",\"text\":\"{}\"}"
                + "],\"stop_reason\":\"end_turn\"}");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> parser.parse("do two things"));

        assertEquals("Model returned multiple commands — only one command per request is permitted", ex.getMessage());
    }

    @Test
    void twoObjectsInOneTextBlock_areRejected() throws Exception {
        stubHttpResponse(200, anthropicResponse("{\"verb\":\"get\",\"resource\":\"pods\",\"namespace\":\"default\"} "
                + "{\"verb\":\"delete\",\"resource\":\"pods\",\"namespace\":\"default\"}"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> parser.parse("do two things"));

        assertEquals("Failed to parse Anthropic response into a command: invalid JSON", ex.getMessage());
    }

    @Test
    void textAfterTheObject_isRejectedWithoutRepeatingIt() throws Exception {
        stubHttpResponse(200, anthropicResponse(
                "{\"verb\":\"get\",\"resource\":\"pods\",\"namespace\":\"default\"} sentinel-prompt-echo"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> parser.parse("anything"));

        assertEquals("Failed to parse Anthropic response into a command: invalid JSON", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void fencedObject_isStillParsed() throws Exception {
        stubHttpResponse(200, anthropicResponse(
                "```json {\"verb\":\"get\",\"resource\":\"pods\",\"namespace\":\"default\"} ```"));

        assertEquals("get", parser.parse("show the pods").getVerb());
    }

    @Test
    void responseWithNoTextBlock_isRejected() throws Exception {
        stubHttpResponse(200, "{\"id\":\"msg_001\",\"content\":["
                + "{\"type\":\"tool_use\",\"id\":\"tu_1\",\"name\":\"kubectl\",\"input\":{}}"
                + "],\"stop_reason\":\"tool_use\"}");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> parser.parse("anything"));

        assertEquals("No text content block in Anthropic response", ex.getMessage());
    }

    @Test
    void missingFields_areReturnedAsEmptyStrings() throws Exception {
        // The parser does not validate fields; an empty verb is left for VerbGuard to reject
        stubHttpResponse(200, anthropicResponse("{\"resource\":\"pods\"}"));

        ParsedCommand command = parser.parse("show the pods");

        assertEquals("", command.getVerb());
        assertEquals("pods", command.getResource());
        assertEquals("", command.getNamespace());
    }

    @Test
    void interruptedCall_restoresTheInterruptFlag() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("sentinel-interrupt"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> parser.parse("anything"));

        // Thread.interrupted() also clears the flag so later tests on this thread are unaffected
        assertTrue(Thread.interrupted());
        assertEquals("Anthropic API call was interrupted", ex.getMessage());
        assertInstanceOf(InterruptedException.class, ex.getCause());
    }

    // --- Outgoing request ---

    @Test
    void request_targetsTheMessagesApiWithTheConfiguredKey() throws Exception {
        stubHttpResponse(200, anthropicResponse("{\"verb\":\"get\",\"resource\":\"pods\",\"namespace\":\"default\"}"));

        parser.parse("show the pods");

        HttpRequest request = capturedRequest();
        assertEquals("POST", request.method());
        assertEquals(URI.create("https://api.anthropic.com/v1/messages"), request.uri());
        assertEquals("test-api-key", request.headers().firstValue("x-api-key").orElse(null));
        assertEquals("2023-06-01", request.headers().firstValue("anthropic-version").orElse(null));
        assertEquals("application/json", request.headers().firstValue("content-type").orElse(null));
    }

    @Test
    void request_usesTheConfiguredModelAndTokenCap() throws Exception {
        AnthropicCommandParser configured = new AnthropicCommandParser(
                httpClient, new ObjectMapper(), "test-api-key", "sentinel-model", 137);
        stubHttpResponse(200, anthropicResponse("{\"verb\":\"get\",\"resource\":\"pods\",\"namespace\":\"default\"}"));

        configured.parse("show the pods");

        JsonNode body = new ObjectMapper().readTree(bodyOf(capturedRequest()));
        assertEquals("sentinel-model", body.path("model").asText());
        assertEquals(137, body.path("max_tokens").asInt());
        assertTrue(body.path("system").asText().contains("single kubectl-style command"),
                body.path("system").asText());
    }

    @Test
    void request_sendsThePromptAsTheOnlyUserMessage() throws Exception {
        stubHttpResponse(200, anthropicResponse("{\"verb\":\"get\",\"resource\":\"pods\",\"namespace\":\"default\"}"));

        parser.parse("sentinel-user-prompt");

        JsonNode messages = new ObjectMapper().readTree(bodyOf(capturedRequest())).path("messages");
        assertEquals(1, messages.size());
        assertEquals("user", messages.get(0).path("role").asText());
        assertEquals("sentinel-user-prompt", messages.get(0).path("content").asText());
    }

    // --- Helpers ---

    @SuppressWarnings("unchecked")
    private HttpRequest capturedRequest() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        return captor.getValue();
    }

    /** Drains the request's body publisher; BodyPublishers.ofString delivers synchronously. */
    private static String bodyOf(HttpRequest request) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                out.write(bytes, 0, bytes.length);
            }

            @Override
            public void onError(Throwable throwable) {
                throw new IllegalStateException(throwable);
            }

            @Override
            public void onComplete() {
            }
        });
        return out.toString(StandardCharsets.UTF_8);
    }

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
