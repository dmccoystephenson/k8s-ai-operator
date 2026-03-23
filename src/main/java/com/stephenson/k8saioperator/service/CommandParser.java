package com.stephenson.k8saioperator.service;

import com.stephenson.k8saioperator.model.ParsedCommand;

/**
 * Translates a natural-language user prompt into a structured {@link ParsedCommand}.
 * Implementations may call different LLM back-ends (AWS Bedrock, Anthropic API, etc.).
 */
public interface CommandParser {

    /**
     * Parses the user prompt into a {@link ParsedCommand}.
     * The user prompt must never be logged.
     *
     * @param userPrompt natural language input from the operator
     * @return structured command parsed by the model
     * @throws IllegalStateException if the model returns an unparseable or multi-command response
     */
    ParsedCommand parse(String userPrompt);
}
