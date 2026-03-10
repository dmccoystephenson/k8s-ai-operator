package com.stephenson.k8saioperator.service;

import com.stephenson.k8saioperator.model.ParsedCommand;

/**
 * Strategy interface for translating a natural-language user prompt into a
 * structured {@link ParsedCommand}.
 *
 * The production implementation calls Amazon Bedrock; the local implementation
 * uses keyword-based heuristics so the application can run without any AWS
 * credentials.
 */
public interface CommandParser {

    /**
     * Parses the user prompt into a {@link ParsedCommand}.
     * Implementations must never log the raw user prompt.
     *
     * @param userPrompt natural language input from the operator
     * @return structured command
     * @throws IllegalStateException if the prompt cannot be resolved to a single command
     */
    ParsedCommand parse(String userPrompt);
}
