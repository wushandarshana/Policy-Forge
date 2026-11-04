package com.horizon.policyforge.service;

import com.horizon.policyforge.domain.endorsement.Address;
import com.horizon.policyforge.domain.endorsement.Nominee;
import com.horizon.policyforge.domain.policy.PolicyStatus;
import com.horizon.policyforge.repository.RefundRecordEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * A policy with its history as the owner (or an ADMIN) sees it (C3): the header, every version, every status transition,
 * every term and the renewal offer waiting to be paid, if any. All of it is built from allow-lists (P-9): the risk inputs
 * of a version are the product's declared inputs without the health inputs, and no KYC field is carried.
 *
 * @param pendingRenewal the unpaid renewal term of an in-force policy, or null when there is none (C5)
 * @param refunds        the refund records of the policy in append order: none until it is cancelled, then one (C5)
 */
public record PolicyDetail(
        PolicyHeader policy,
        List<VersionView> versions,
        List<EndorsementView> endorsements,
        List<TransitionView> transitions,
        List<TermView> terms,
        PendingRenewalView pendingRenewal,
        List<RefundView> refunds) {

    public PolicyDetail {
        versions = List.copyOf(versions);
        endorsements = List.copyOf(endorsements);
        transitions = List.copyOf(transitions);
        terms = List.copyOf(terms);
        refunds = List.copyOf(refunds);
    }

    /** One version, ascending by number. The address and the nominees are personal data: the string form hides them. */
    public record VersionView(
            int versionNo,
            LocalDate effectiveDate,
            LocalDate expiryDate,
            BigDecimal sumInsured,
            BigDecimal annualPremium,
            int ruleVersion,
            Map<String, Object> riskInputs,
            Address address,
            List<Nominee> nominees,
            Instant createdAt) {

        @Override
        public String toString() {
            return "VersionView[versionNo=" + versionNo + "]";
        }
    }

    /**
     * One endorsement, ascending by sequence number. {@code changes} is built from an allow-list per type: {@code
     * address}, {@code nominee}, {@code newSumInsured} or the building and contents keys. It is personal data for the
     * first two, so the string form hides it.
     */
    public record EndorsementView(
            int sequenceNo,
            String type,
            Map<String, Object> changes,
            int beforeVersionNo,
            int afterVersionNo,
            BigDecimal newAnnualPremium,
            BigDecimal adjustment,
            LocalDate effectiveDate,
            String actorId,
            Instant createdAt) {

        @Override
        public String toString() {
            return "EndorsementView[sequenceNo=" + sequenceNo + "]";
        }
    }

    /** One status change, in append order; {@code fromStatus} is null for the move that created the policy. */
    public record TransitionView(PolicyStatus fromStatus, PolicyStatus toStatus, String reason, String actorId,
            Instant at) {
    }

    /** One cover term, ascending by number, with the rule version its premium was rated with. */
    public record TermView(int termNo, LocalDate startDate, LocalDate endDate, BigDecimal premium, boolean paid,
            int ruleVersion) {
    }

    /**
     * The renewal offer an in-force policy can still pay (C5): the unpaid term above the current paid term, and the last
     * day it can be paid, which is the current paid term's end plus its grace days.
     */
    public record PendingRenewalView(int termNo, LocalDate startDate, LocalDate endDate, BigDecimal premium,
            int ruleVersion, LocalDate payableUntil) {
    }

    /**
     * One refund record (C5): the term it was calculated on, the premium basis, the day counts, the fee, the amount and
     * the formula string, with who cancelled and when. Numbers, dates and codes only (P-9).
     */
    public record RefundView(int termNo, LocalDate cancellationDate, BigDecimal premium, int termDays,
            int unexpiredDays, boolean coolingOff, BigDecimal fee, BigDecimal amount, String formula, String actorId,
            Instant createdAt) {

        static RefundView of(RefundRecordEntity record) {
            return new RefundView(record.getTermNo(), record.getCancellationDate(), record.getPremium(),
                    record.getTermDays(), record.getUnexpiredDays(), record.isCoolingOff(), record.getFee(),
                    record.getAmount(), record.getFormula(), record.getActorId(), record.getCreatedAt());
        }
    }
}
