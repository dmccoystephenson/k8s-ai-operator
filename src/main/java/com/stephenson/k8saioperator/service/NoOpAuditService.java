package com.stephenson.k8saioperator.service;

import com.stephenson.k8saioperator.model.ParsedCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Local (no-AWS) implementation of {@link AuditPort}.
 *
 * Writes audit records to the application log instead of Amazon DynamoDB.
 * Activate this bean by running with the {@code local} Spring profile.
 */
@Slf4j
@Service
@Profile("local")
public class NoOpAuditService implements AuditPort {

    @Override
    public void recordAllowed(String requestId, ParsedCommand command, long latencyMs) {
        log.info("[LOCAL AUDIT] ALLOWED  request_id={} verb={} resource={} namespace={} latency_ms={}",
                requestId,
                command != null ? command.getVerb() : "n/a",
                command != null ? command.getResource() : "n/a",
                command != null ? command.getNamespace() : "n/a",
                latencyMs);
    }

    @Override
    public void recordBlocked(String requestId, ParsedCommand command, String blockReason, long latencyMs) {
        log.info("[LOCAL AUDIT] BLOCKED  request_id={} verb={} reason='{}' latency_ms={}",
                requestId,
                command != null ? command.getVerb() : "n/a",
                blockReason,
                latencyMs);
    }
}
