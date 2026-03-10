package com.stephenson.k8saioperator.service;

import com.stephenson.k8saioperator.model.ParsedCommand;

/**
 * Strategy interface for persisting audit records.
 *
 * The production implementation writes to Amazon DynamoDB; the local
 * implementation logs to the console so the application can run without
 * any AWS credentials.
 */
public interface AuditPort {

    /**
     * Records a successfully executed (allowed) command.
     */
    void recordAllowed(String requestId, ParsedCommand command, long latencyMs);

    /**
     * Records a blocked command along with the reason it was blocked.
     */
    void recordBlocked(String requestId, ParsedCommand command, String blockReason, long latencyMs);
}
