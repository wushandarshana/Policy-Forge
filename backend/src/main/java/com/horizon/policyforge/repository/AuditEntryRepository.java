package com.horizon.policyforge.repository;

import java.util.List;
import org.springframework.data.repository.Repository;

/** Append-only access to the audit trail (NFR-02): {@code save} and finders only, no delete, no update. */
public interface AuditEntryRepository extends Repository<AuditEntryEntity, Long> {

    AuditEntryEntity save(AuditEntryEntity entity);

    List<AuditEntryEntity> findByEntityTypeAndEntityIdOrderByIdAsc(String entityType, String entityId);

    List<AuditEntryEntity> findAllByOrderByIdAsc();
}
