package com.horizon.policyforge.service;

import com.horizon.policyforge.domain.endorsement.Address;
import com.horizon.policyforge.domain.endorsement.Nominee;
import com.horizon.policyforge.domain.policy.PolicyStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * A policy with its history as the owner (or an ADMIN) sees it (C3): the header, every version, every status transition
 * and every term. All of it is built from allow-lists (P-9): the risk inputs of a version are the product's declared
 * inputs without the health inputs, and no KYC field is carried.
 */
public record PolicyDetail(
        PolicyHeader policy,
        List<VersionView> versions,
        List<TransitionView> transitions,
        List<TermView> terms) {

    public PolicyDetail {
        versions = List.copyOf(versions);
        transitions = List.copyOf(transitions);
        terms = List.copyOf(terms);
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

    /** One status change, in append order; {@code fromStatus} is null for the move that created the policy. */
    public record TransitionView(PolicyStatus fromStatus, PolicyStatus toStatus, String reason, String actorId,
            Instant at) {
    }

    /** One cover term, ascending by number. */
    public record TermView(int termNo, LocalDate startDate, LocalDate endDate, BigDecimal premium, boolean paid) {
    }
}
