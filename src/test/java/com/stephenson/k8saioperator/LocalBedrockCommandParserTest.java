package com.stephenson.k8saioperator;

import com.stephenson.k8saioperator.config.LocalBedrockCommandParser;
import com.stephenson.k8saioperator.model.ParsedCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LocalBedrockCommandParser}.
 * Verifies keyword-based NLP heuristics used when the {@code local} profile is active.
 */
class LocalBedrockCommandParserTest {

    private LocalBedrockCommandParser parser;

    @BeforeEach
    void setUp() {
        parser = new LocalBedrockCommandParser();
    }

    @Test
    void defaultsToGetPodsInDefaultNamespace() {
        ParsedCommand cmd = parser.parse("what is running");
        assertEquals("get", cmd.getVerb());
        assertEquals("pods", cmd.getResource());
        assertEquals("default", cmd.getNamespace());
    }

    @Test
    void resolvesGetPodsWithNamespace() {
        ParsedCommand cmd = parser.parse("show me the pods in production");
        assertEquals("get", cmd.getVerb());
        assertEquals("pods", cmd.getResource());
        assertEquals("production", cmd.getNamespace());
    }

    @Test
    void resolvesGetDeployments() {
        ParsedCommand cmd = parser.parse("list all deployments in staging");
        assertEquals("get", cmd.getVerb());
        assertEquals("deployments", cmd.getResource());
        assertEquals("staging", cmd.getNamespace());
    }

    @Test
    void resolvesGetServices() {
        ParsedCommand cmd = parser.parse("show services in namespace kube-system");
        assertEquals("get", cmd.getVerb());
        assertEquals("services", cmd.getResource());
        assertEquals("kube-system", cmd.getNamespace());
    }

    @Test
    void resolvesApplyVerb() {
        ParsedCommand cmd = parser.parse("apply the deployment yaml to staging");
        assertEquals("apply", cmd.getVerb());
    }

    @Test
    void resolvesCreateVerbAsApply() {
        ParsedCommand cmd = parser.parse("create a new deployment in staging");
        assertEquals("apply", cmd.getVerb());
    }

    @Test
    void resolvesDeleteVerb() {
        ParsedCommand cmd = parser.parse("delete the crashed pod in default");
        assertEquals("delete", cmd.getVerb());
    }

    @Test
    void resolvesExecVerb() {
        ParsedCommand cmd = parser.parse("exec into the running pod");
        assertEquals("exec", cmd.getVerb());
    }

    @Test
    void resolvesScaleVerb() {
        ParsedCommand cmd = parser.parse("scale the deployment to 3 replicas");
        assertEquals("scale", cmd.getVerb());
    }

    @Test
    void handleNullPromptGracefully() {
        ParsedCommand cmd = parser.parse(null);
        assertEquals("get", cmd.getVerb());
        assertEquals("pods", cmd.getResource());
        assertEquals("default", cmd.getNamespace());
    }

    @Test
    void extractsNamespaceFromInPattern() {
        ParsedCommand cmd = parser.parse("show pods in my-namespace");
        assertEquals("my-namespace", cmd.getNamespace());
    }

    @Test
    void extractsNamespaceFromNamespacePattern() {
        ParsedCommand cmd = parser.parse("list services namespace monitoring");
        assertEquals("monitoring", cmd.getNamespace());
    }
}
