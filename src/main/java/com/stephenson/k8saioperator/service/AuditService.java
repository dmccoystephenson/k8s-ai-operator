package com.stephenson.k8saioperator.service;

import com.stephenson.k8saioperator.model.ParsedCommand;

/**
 * Writes an audit record for every request — allowed or blocked.
 * Raw user prompts are NEVER stored.
 */
public interface AuditService {

    /**
     * Persists an allowed execution to the audit store.
     */
    void recordAllowed(String requestId, ParsedCommand command, long latencyMs);

    /**
     * Persists a blocked execution to the audit store, including the block reason.
     */
    void recordBlocked(String requestId, ParsedCommand command, String blockReason, long latencyMs);
}

