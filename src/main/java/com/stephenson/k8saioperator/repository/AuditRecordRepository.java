package com.stephenson.k8saioperator.repository;

import com.stephenson.k8saioperator.model.AuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link AuditRecord}.
 * Only instantiated when the {@code local} Spring profile is active
 * (JPA autoconfiguration is excluded in other profiles).
 */
public interface AuditRecordRepository extends JpaRepository<AuditRecord, String> {
}
