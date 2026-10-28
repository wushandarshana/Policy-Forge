package com.horizon.policyforge.service;

import com.horizon.policyforge.repository.AuditEntryEntity;
import com.horizon.policyforge.repository.AuditEntryRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the append-only audit trail (NFR-02, NFR-04). The actor ID is passed in by the caller, who got it from the
 * authenticated principal in the web layer. It joins the caller's transaction, so an action and its audit entry commit
 * or roll back together.
 */
@Service
public class AuditService {

    private final AuditEntryRepository entries;
    private final Clock clock;

    public AuditService(AuditEntryRepository entries, Clock clock) {
        this.entries = entries;
        this.clock = clock;
    }

    @Transactional
    public void record(String actorId, String action, String entityType, String entityId) {
        record(actorId, action, entityType, entityId, null, null);
    }

    @Transactional
    public void record(String actorId, String action, String entityType, String entityId, String reasonCode,
            String comment) {
        entries.save(AuditEntryEntity.of(actorId, action, entityType, entityId, reasonCode, comment,
                Instant.now(clock)));
    }
}
