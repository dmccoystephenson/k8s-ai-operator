package com.stephenson.k8saioperator.service;

import com.stephenson.k8saioperator.model.AuditRecord;
import com.stephenson.k8saioperator.model.ParsedCommand;
import com.stephenson.k8saioperator.repository.AuditRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Writes a full audit record to PostgreSQL for every request — allowed or blocked.
 * Active only when the {@code local} Spring profile is active.
 * Raw user prompts are NEVER stored.
 */
@Slf4j
@Service
@Profile("local")
@RequiredArgsConstructor
public class PostgresAuditService implements AuditService {

    private final AuditRecordRepository repository;

    @Override
    public void recordAllowed(String requestId, ParsedCommand command, long latencyMs) {
        AuditRecord record = buildRecord(requestId, command, latencyMs);
        record.setAllowed(true);
        persist(requestId, record);
    }

    @Override
    public void recordBlocked(String requestId, ParsedCommand command, String blockReason, long latencyMs) {
        AuditRecord record = buildRecord(requestId, command, latencyMs);
        record.setAllowed(false);
        record.setBlockReason(blockReason != null && !blockReason.isBlank() ? blockReason : null);
        persist(requestId, record);
    }

    private AuditRecord buildRecord(String requestId, ParsedCommand command, long latencyMs) {
        AuditRecord.AuditRecordBuilder builder = AuditRecord.builder()
                .requestId(requestId)
                .timestamp(Instant.now())
                .executionLatencyMs(latencyMs);

        if (command != null) {
            builder.parsedVerb(command.getVerb())
                    .parsedResource(command.getResource())
                    .parsedNamespace(command.getNamespace());
        }

        return builder.build();
    }

    private void persist(String requestId, AuditRecord record) {
        try {
            repository.save(record);
            log.debug("Audit record written for request_id={}", requestId);
        } catch (Exception e) {
            log.error("Failed to write audit record for request_id={}", requestId, e);
        }
    }
}
