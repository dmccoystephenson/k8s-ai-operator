package com.stephenson.k8saioperator.config;

import com.stephenson.k8saioperator.model.ParsedCommand;
import com.stephenson.k8saioperator.service.CommandParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Local (no-AWS) implementation of {@link CommandParser}.
 *
 * Uses simple keyword heuristics to translate natural-language prompts into a
 * {@link ParsedCommand} without calling Amazon Bedrock. Activate this bean by
 * running the application with the {@code local} Spring profile
 * (e.g. {@code --spring.profiles.active=local}).
 *
 * <p>Supported keywords:
 * <ul>
 *   <li>Verbs: {@code get} (default), {@code apply} — recognised by keywords in the prompt</li>
 *   <li>Resources: {@code pods} (default), {@code deployments}, {@code services}</li>
 *   <li>Namespace: extracted from "in &lt;namespace&gt;" / "namespace &lt;namespace&gt;" patterns,
 *       defaults to {@code default}</li>
 * </ul>
 */
@Slf4j
@Service
@Profile("local")
public class LocalBedrockCommandParser implements CommandParser {

    @Override
    public ParsedCommand parse(String userPrompt) {
        log.debug("LocalBedrockCommandParser parsing prompt (content not logged)");

        String lower = userPrompt == null ? "" : userPrompt.toLowerCase();

        String verb = resolveVerb(lower);
        String resource = resolveResource(lower);
        String namespace = resolveNamespace(lower);

        ParsedCommand command = ParsedCommand.builder()
                .verb(verb)
                .resource(resource)
                .namespace(namespace)
                .build();

        log.info("[LOCAL] Parsed command: verb={} resource={} namespace={}", verb, resource, namespace);
        return command;
    }

    private String resolveVerb(String lower) {
        if (lower.contains("apply") || lower.contains("create")) {
            return "apply";
        }
        if (lower.contains("delete") || lower.contains("remove")) {
            return "delete";
        }
        if (lower.contains("scale")) {
            return "scale";
        }
        if (lower.contains("exec") || lower.contains("shell") || lower.contains("bash")) {
            return "exec";
        }
        if (lower.contains("patch") || lower.contains("update")) {
            return "patch";
        }
        // default to "get" for show/list/describe/get queries
        return "get";
    }

    private String resolveResource(String lower) {
        if (lower.contains("deployment")) {
            return "deployments";
        }
        if (lower.contains("service") || lower.contains("svc")) {
            return "services";
        }
        // default to pods
        return "pods";
    }

    private String resolveNamespace(String lower) {
        // Look for "namespace <name>" first (higher priority), then "in <name>"
        String[] tokens = lower.split("\\s+");
        for (int i = 0; i < tokens.length - 1; i++) {
            if ("namespace".equals(tokens[i])) {
                String candidate = tokens[i + 1].replaceAll("[^a-z0-9-]", "");
                if (!candidate.isEmpty()) {
                    return candidate;
                }
            }
        }
        for (int i = 0; i < tokens.length - 1; i++) {
            if ("in".equals(tokens[i]) && !"namespace".equals(tokens[i + 1])) {
                String candidate = tokens[i + 1].replaceAll("[^a-z0-9-]", "");
                if (!candidate.isEmpty()) {
                    return candidate;
                }
            }
        }
        return "default";
    }
}
