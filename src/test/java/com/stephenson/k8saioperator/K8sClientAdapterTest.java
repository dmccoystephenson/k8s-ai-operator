package com.stephenson.k8saioperator;

import com.stephenson.k8saioperator.model.ParsedCommand;
import com.stephenson.k8saioperator.service.K8sClientAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates that K8sClientAdapter dispatches on verb and resource
 * case-insensitively, returns the simulated listing for each supported
 * resource type with the requested namespace appended, formats apply results,
 * and returns a descriptive message for unrecognised verbs and resource types.
 */
class K8sClientAdapterTest {

    private K8sClientAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new K8sClientAdapter();
    }

    private static ParsedCommand command(String verb, String resource, String namespace) {
        return ParsedCommand.builder().verb(verb).resource(resource).namespace(namespace).build();
    }

    // --- get ---

    @Test
    void getPods_returnsPodListingWithNamespace() {
        String result = adapter.execute(command("get", "pods", "team-a"));

        assertTrue(result.startsWith("NAME"), result);
        assertTrue(result.contains("STATUS"), result);
        assertTrue(result.contains("app-deployment-7d4f9b-xk2p9"), result);
        assertTrue(result.contains("db-statefulset-0"), result);
        assertTrue(result.endsWith("Namespace: team-a"), result);
    }

    @Test
    void getDeployments_returnsDeploymentListingWithNamespace() {
        String result = adapter.execute(command("get", "deployments", "team-a"));

        assertTrue(result.contains("UP-TO-DATE"), result);
        assertTrue(result.contains("app-deployment"), result);
        assertTrue(result.contains("worker"), result);
        assertTrue(result.endsWith("Namespace: team-a"), result);
    }

    @Test
    void getServices_returnsServiceListingWithNamespace() {
        String result = adapter.execute(command("get", "services", "team-a"));

        assertTrue(result.contains("CLUSTER-IP"), result);
        assertTrue(result.contains("app-svc"), result);
        assertTrue(result.contains("db-svc"), result);
        assertTrue(result.endsWith("Namespace: team-a"), result);
    }

    @Test
    void getUnknownResource_returnsNoResourcesFoundMessage() {
        assertEquals("No resources found for type: configmaps",
                adapter.execute(command("get", "configmaps", "default")));
    }

    @Test
    void getSingularResource_isNotMatchedToPluralListing() {
        assertEquals("No resources found for type: pod",
                adapter.execute(command("get", "pod", "default")));
    }

    // --- apply ---

    @Test
    void apply_returnsAppliedMessageWithResourceAndNamespace() {
        assertEquals("deployment \"manifest\" applied in namespace \"prod\"",
                adapter.execute(command("apply", "deployment", "prod")));
    }

    @Test
    void apply_preservesResourceCaseInMessage() {
        assertEquals("ConfigMap \"manifest\" applied in namespace \"prod\"",
                adapter.execute(command("apply", "ConfigMap", "prod")));
    }

    // --- Case insensitivity ---

    @Test
    void upperCaseVerb_isDispatchedLikeLowerCase() {
        assertEquals(adapter.execute(command("get", "pods", "default")),
                adapter.execute(command("GET", "pods", "default")));
    }

    @Test
    void mixedCaseResource_isDispatchedLikeLowerCase() {
        assertEquals(adapter.execute(command("get", "deployments", "default")),
                adapter.execute(command("get", "Deployments", "default")));
    }

    // --- Unrecognised verbs ---

    @Test
    void unrecognisedVerb_returnsUnrecognisedMessageWithOriginalVerb() {
        assertEquals("Unrecognised verb: Describe",
                adapter.execute(command("Describe", "pods", "default")));
    }

    @Test
    void deleteVerb_isNotExecutedAndReturnsUnrecognisedMessage() {
        assertEquals("Unrecognised verb: delete",
                adapter.execute(command("delete", "pods", "default")));
    }
}
