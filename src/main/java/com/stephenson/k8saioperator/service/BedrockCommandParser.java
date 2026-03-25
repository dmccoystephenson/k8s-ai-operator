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
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.util.List;

/**
 * Calls Amazon Bedrock (Claude) to translate a natural-language user prompt
 * into a structured {@link ParsedCommand}.
 *
 * Enforces:
 * - Max 1 command per request (multi-command responses → exception)
 * - Max token cap on the Bedrock call
 * - User prompts are never logged
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "bedrock", matchIfMissing = true)
public class BedrockCommandParser implements CommandParser {

    private static final String SYSTEM_PROMPT =
            "You are a Kubernetes operations assistant. Translate the user's natural language " +
            "request into a single kubectl-style command as a JSON object with exactly three fields: " +
            "\"verb\" (one of: get, apply, delete, exec, scale, patch), " +
            "\"resource\" (one of: pods, deployments, services), and " +
            "\"namespace\" (the target namespace string). " +
            "Return ONLY the raw JSON object with no markdown, no explanation, and no extra text. " +
            "If the user's intent maps to more than one command, return only the most important one.";

    private final BedrockRuntimeClient bedrockClient;
    private final ObjectMapper objectMapper;
    private final String modelId;
    private final int maxTokens;

    public BedrockCommandParser(
            BedrockRuntimeClient bedrockClient,
            ObjectMapper objectMapper,
            @Value("${aws.bedrock.model-id}") String modelId,
            @Value("${aws.bedrock.max-tokens}") int maxTokens) {
        this.bedrockClient = bedrockClient;
        this.objectMapper = objectMapper;
        this.modelId = modelId;
        this.maxTokens = maxTokens;
    }

    /**
     * Parses the user prompt into a {@link ParsedCommand}.
     * The user prompt is never logged.
     *
     * @param userPrompt natural language input from the operator
     * @return structured command parsed by the model
     * @throws IllegalStateException if the model returns an unparseable or multi-command response
     */
    public ParsedCommand parse(String userPrompt) {
        String requestBody = buildRequestBody(userPrompt);

        log.debug("Invoking Bedrock model: {}", modelId);

        InvokeModelRequest request = InvokeModelRequest.builder()
                .modelId(modelId)
                .contentType("application/json")
                .accept("application/json")
                .body(SdkBytes.fromUtf8String(requestBody))
                .build();

        InvokeModelResponse response = bedrockClient.invokeModel(request);
        String responseBody = response.body().asUtf8String();

        return parseResponse(responseBody);
    }

    private String buildRequestBody(String userPrompt) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("anthropic_version", "bedrock-2023-05-31");
            root.put("max_tokens", maxTokens);
            root.put("system", SYSTEM_PROMPT);

            ArrayNode messages = objectMapper.createArrayNode();
            ObjectNode message = objectMapper.createObjectNode();
            message.put("role", "user");

            ArrayNode content = objectMapper.createArrayNode();
            ObjectNode textBlock = objectMapper.createObjectNode();
            textBlock.put("type", "text");
            textBlock.put("text", userPrompt);
            content.add(textBlock);
            message.set("content", content);
            messages.add(message);
            root.set("messages", messages);

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build Bedrock request body", e);
        }
    }

    private ParsedCommand parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode contentArray = root.path("content");

            if (!contentArray.isArray() || contentArray.isEmpty()) {
                throw new IllegalStateException("Bedrock returned empty content array");
            }

            // Collect all text blocks — reject if more than one command is present
            List<JsonNode> textBlocks = new java.util.ArrayList<>();
            for (JsonNode block : contentArray) {
                if ("text".equals(block.path("type").asText())) {
                    textBlocks.add(block);
                }
            }

            if (textBlocks.isEmpty()) {
                throw new IllegalStateException("No text content block in Bedrock response");
            }

            // Extract the text from the first (and only expected) block
            String jsonText = textBlocks.get(0).path("text").asText().trim();

            // Strip any accidental markdown fencing
            if (jsonText.startsWith("```")) {
                jsonText = jsonText.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
            }

            JsonNode commandNode = objectMapper.readTree(jsonText);

            // Reject multi-command responses — the model must return a single object, not an array
            if (commandNode.isArray()) {
                throw new IllegalStateException("Model returned multiple commands — only one command per request is permitted");
            }

            return ParsedCommand.builder()
                    .verb(commandNode.path("verb").asText())
                    .resource(commandNode.path("resource").asText())
                    .namespace(commandNode.path("namespace").asText())
                    .build();

        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Bedrock response into a command: " + e.getMessage(), e);
        }
    }
}

