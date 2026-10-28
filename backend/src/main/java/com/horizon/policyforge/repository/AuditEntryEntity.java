package com.horizon.policyforge.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/**
 * One audit entry: which actor did what to which record, and when (NFR-02, NFR-04). Append-only: built once through
 * {@link #of}, immutable to Hibernate, no setters.
 */
@Entity
@Immutable
@Table(name = "audit_entry")
public class AuditEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private String actorId;

    @Column(name = "action", nullable = false, updatable = false)
    private String action;

    @Column(name = "entity_type", nullable = false, updatable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private String entityId;

    @Column(name = "reason_code", updatable = false)
    private String reasonCode;

    @Column(name = "comment_text", updatable = false)
    private String comment;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    /** For JPA only. */
    protected AuditEntryEntity() {
    }

    private AuditEntryEntity(String actorId, String action, String entityType, String entityId, String reasonCode,
            String comment, Instant occurredAt) {
        this.actorId = actorId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.reasonCode = reasonCode;
        this.comment = comment;
        this.occurredAt = occurredAt;
    }

    public static AuditEntryEntity of(String actorId, String action, String entityType, String entityId,
            String reasonCode, String comment, Instant occurredAt) {
        return new AuditEntryEntity(actorId, action, entityType, entityId, reasonCode, comment, occurredAt);
    }

    public Long getId() {
        return id;
    }

    public String getActorId() {
        return actorId;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getComment() {
        return comment;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
