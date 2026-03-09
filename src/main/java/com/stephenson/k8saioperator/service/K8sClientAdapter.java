package com.stephenson.k8saioperator.service;

import com.stephenson.k8saioperator.model.ParsedCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Mock Kubernetes API client.
 * Executes the parsed command against a simulated cluster and returns
 * a human-readable result string.
 */
@Slf4j
@Service
public class K8sClientAdapter {

    /**
     * Executes a validated {@link ParsedCommand} and returns a mock result.
     *
     * @param command the command to execute
     * @return simulated kubectl output
     */
    public String execute(ParsedCommand command) {
        log.debug("Executing command: verb={} resource={} namespace={}",
                command.getVerb(), command.getResource(), command.getNamespace());

        return switch (command.getVerb().toLowerCase()) {
            case "get"   -> simulateGet(command);
            case "apply" -> simulateApply(command);
            default      -> "Unrecognised verb: " + command.getVerb();
        };
    }

    private String simulateGet(ParsedCommand command) {
        return switch (command.getResource().toLowerCase()) {
            case "pods"        -> mockPodList(command.getNamespace());
            case "deployments" -> mockDeploymentList(command.getNamespace());
            case "services"    -> mockServiceList(command.getNamespace());
            default            -> "No resources found for type: " + command.getResource();
        };
    }

    private String simulateApply(ParsedCommand command) {
        return String.format("%s \"%s\" applied in namespace \"%s\"",
                command.getResource(), "manifest", command.getNamespace());
    }

    private String mockPodList(String namespace) {
        return String.format("""
                NAME                          READY   STATUS    RESTARTS   AGE
                app-deployment-7d4f9b-xk2p9   1/1     Running   0          2d
                app-deployment-7d4f9b-lm3q7   1/1     Running   0          2d
                db-statefulset-0              1/1     Running   1          5d
                Namespace: %s""", namespace);
    }

    private String mockDeploymentList(String namespace) {
        return String.format("""
                NAME             READY   UP-TO-DATE   AVAILABLE   AGE
                app-deployment   2/2     2            2           5d
                worker           1/1     1            1           3d
                Namespace: %s""", namespace);
    }

    private String mockServiceList(String namespace) {
        return String.format("""
                NAME         TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)    AGE
                app-svc      ClusterIP   10.100.200.10   <none>        80/TCP     5d
                db-svc       ClusterIP   10.100.200.11   <none>        5432/TCP   5d
                Namespace: %s""", namespace);
    }
}

