package com.stephenson.k8saioperator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stephenson.k8saioperator.model.ParsedCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

/**
 * Calls the Anthropic Messages API directly to translate a natural-language
 * user prompt into a structured {@link ParsedCommand}.
 *
 * Enforces:
 * - Max 1 command per request (multi-command responses → exception)
 * - Max token cap on the API call
 * - User prompts are never logged
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "anthropic")
public class AnthropicCommandParser implements CommandParser {

    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private static final String SYSTEM_PROMPT =
            "You are a Kubernetes operations assistant. Translate the user's natural language " +
            "request into a single kubectl-style command as a JSON object with exactly three fields: " +
            "\"verb\" (one of: get, apply, delete, exec, scale, patch), " +
            "\"resource\" (one of: pods, deployments, services), and " +
            "\"namespace\" (the target namespace string). " +
            "Return ONLY the raw JSON object with no markdown, no explanation, and no extra text. " +
            "If the user's intent maps to more than one command, return only the most important one.";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final int maxTokens;

    public AnthropicCommandParser(
            ObjectMapper objectMapper,
            @Value("${anthropic.api-key}") String apiKey,
            @Value("${anthropic.model}") String model,
            @Value("${anthropic.max-tokens}") int maxTokens) {
        this(HttpClient.newHttpClient(), objectMapper, apiKey, model, maxTokens);
    }

    public AnthropicCommandParser(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String apiKey,
            String model,
            int maxTokens) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    @Override
    public ParsedCommand parse(String userPrompt) {
        String requestBody = buildRequestBody(userPrompt);

        log.debug("Invoking Anthropic model: {}", model);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ANTHROPIC_API_URL))
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Anthropic API returned status " + response.statusCode() + ": " + response.body());
            }

            return parseResponse(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to call Anthropic API: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Anthropic API call was interrupted", e);
        }
    }

    private String buildRequestBody(String userPrompt) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", model);
            root.put("max_tokens", maxTokens);
            root.put("system", SYSTEM_PROMPT);

            ArrayNode messages = objectMapper.createArrayNode();
            ObjectNode message = objectMapper.createObjectNode();
            message.put("role", "user");
            message.put("content", userPrompt);
            messages.add(message);
            root.set("messages", messages);

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build Anthropic request body", e);
        }
    }

    private ParsedCommand parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode contentArray = root.path("content");

            if (!contentArray.isArray() || contentArray.isEmpty()) {
                throw new IllegalStateException("Anthropic API returned empty content array");
            }

            List<JsonNode> textBlocks = new java.util.ArrayList<>();
            for (JsonNode block : contentArray) {
                if ("text".equals(block.path("type").asText())) {
                    textBlocks.add(block);
                }
            }

            if (textBlocks.isEmpty()) {
                throw new IllegalStateException("No text content block in Anthropic response");
            }

            String jsonText = textBlocks.get(0).path("text").asText().trim();

            // Strip any accidental markdown fencing
            if (jsonText.startsWith("```")) {
                jsonText = jsonText.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
            }

            JsonNode commandNode = objectMapper.readTree(jsonText);

            if (commandNode.isArray()) {
                throw new IllegalStateException(
                        "Model returned multiple commands — only one command per request is permitted");
            }

            return ParsedCommand.builder()
                    .verb(commandNode.path("verb").asText())
                    .resource(commandNode.path("resource").asText())
                    .namespace(commandNode.path("namespace").asText())
                    .build();

        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to parse Anthropic response into a command: " + e.getMessage(), e);
        }
    }
}
