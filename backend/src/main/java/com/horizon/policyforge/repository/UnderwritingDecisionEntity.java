package com.horizon.policyforge.repository;

import com.horizon.policyforge.domain.application.DecisionSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/**
 * One underwriting decision (AC-04, U-9): the automated {@code RULES} evaluation, a {@code MANUAL} decision or an
 * {@code OVERRIDE}. Append-only (NFR-02): built once through {@link #of}, immutable to Hibernate, no setters. A later
 * decision is a new row, so the original automated decision is always kept.
 */
@Entity
@Immutable
@Table(name = "underwriting_decision")
public class UnderwritingDecisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private String applicationId;

    /** The {@link DecisionSource} name. */
    @Column(name = "source", nullable = false, updatable = false)
    private String source;

    /** AUTO_BIND, MANUAL_REVIEW or DECLINE for RULES; APPROVE or DECLINE for MANUAL; APPROVE for OVERRIDE. */
    @Column(name = "outcome", nullable = false, updatable = false)
    private String outcome;

    /** The reason codes, sorted and comma separated. */
    @Column(name = "reason_codes", nullable = false, updatable = false)
    private String reasonCodes;

    /** The reasons as a JSON array of {@code {code, description}}, in the same order as {@code reasonCodes}. */
    @Lob
    @Column(name = "reasons_json", nullable = false, updatable = false)
    private String reasonsJson;

    @Column(name = "rule_version", nullable = false, updatable = false)
    private int ruleVersion;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private String actorId;

    @Column(name = "comment_text", updatable = false)
    private String comment;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;

    /** For JPA only. */
    protected UnderwritingDecisionEntity() {
    }

    private UnderwritingDecisionEntity(String applicationId, DecisionSource source, String outcome,
            String reasonCodes, String reasonsJson, int ruleVersion, String actorId, String comment,
            Instant decidedAt) {
        this.applicationId = applicationId;
        this.source = source.name();
        this.outcome = outcome;
        this.reasonCodes = reasonCodes;
        this.reasonsJson = reasonsJson;
        this.ruleVersion = ruleVersion;
        this.actorId = actorId;
        this.comment = comment;
        this.decidedAt = decidedAt;
    }

    public static UnderwritingDecisionEntity of(String applicationId, DecisionSource source, String outcome,
            String reasonCodes, String reasonsJson, int ruleVersion, String actorId, String comment,
            Instant decidedAt) {
        return new UnderwritingDecisionEntity(applicationId, source, outcome, reasonCodes, reasonsJson, ruleVersion,
                actorId, comment, decidedAt);
    }

    public Long getId() {
        return id;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public DecisionSource getSource() {
        return DecisionSource.valueOf(source);
    }

    public String getOutcome() {
        return outcome;
    }

    public String getReasonCodes() {
        return reasonCodes;
    }

    public String getReasonsJson() {
        return reasonsJson;
    }

    public int getRuleVersion() {
        return ruleVersion;
    }

    public String getActorId() {
        return actorId;
    }

    public String getComment() {
        return comment;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
