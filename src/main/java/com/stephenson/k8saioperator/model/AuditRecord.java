package com.stephenson.k8saioperator.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JPA entity that mirrors the {@code K8sAgentExecutions} DynamoDB schema.
 * Used by {@link com.stephenson.k8saioperator.service.PostgresAuditService}
 * when the {@code local} Spring profile is active.
 * Raw user prompts are NEVER stored.
 */
@Entity
@Table(name = "audit_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditRecord {

    @Id
    @Column(name = "request_id", nullable = false)
    private String requestId;

    @Column(name = "timestamp", nullable = false)
    private String timestamp;

    @Column(name = "execution_latency_ms")
    private Long executionLatencyMs;

    @Column(name = "parsed_verb")
    private String parsedVerb;

    @Column(name = "parsed_resource")
    private String parsedResource;

    @Column(name = "parsed_namespace")
    private String parsedNamespace;

    @Column(name = "allowed", nullable = false)
    private Boolean allowed;

    @Column(name = "block_reason")
    private String blockReason;
}
