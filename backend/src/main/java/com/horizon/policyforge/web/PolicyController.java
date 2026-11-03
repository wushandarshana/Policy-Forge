package com.horizon.policyforge.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.domain.endorsement.Address;
import com.horizon.policyforge.domain.endorsement.Nominee;
import com.horizon.policyforge.service.IssuePolicyCommand;
import com.horizon.policyforge.service.PolicyDetail;
import com.horizon.policyforge.service.PolicyHeader;
import com.horizon.policyforge.service.PolicyIssuanceService;
import com.horizon.policyforge.service.PolicyQueryService;
import com.horizon.policyforge.service.PolicySummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Policy issuance and viewing (AC-05, AC-06): issue an APPROVED application (C1), list the caller's policies (C2) and
 * read one policy's detail (C3), which includes its endorsement history. Issuing and listing are CUSTOMER only; the detail is for the owning CUSTOMER or an ADMIN. Each
 * handler declares its role with {@code @PreAuthorize} and takes the actor from the authenticated principal. It maps
 * requests to {@link PolicyIssuanceService} and {@link PolicyQueryService} and results to DTOs built from allow-lists
 * (KD-008). No business logic lives here.
 *
 * <p>The issue request captures, rather than ignores, any key the contract does not define, so the service can reject it
 * by name (KD-008).
 */
@RestController
@RequestMapping("/api")
public class PolicyController {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    /** The body of {@code POST /api/applications/{id}/issue}: {@code {}} or {@code {effectiveDate}}. */
    public static final class IssueRequest {
        private String effectiveDate;
        private final Set<String> unknownKeys = new TreeSet<>();

        /**
         * Takes the date as received: a JSON string is kept, an absent or null value means "default to today", and
         * anything else (a number, an object) becomes an empty string, which the service refuses as not a date.
         */
        public void setEffectiveDate(JsonNode value) {
            if (value == null || value.isNull()) {
                this.effectiveDate = null;
            } else {
                this.effectiveDate = value.isTextual() ? value.asText() : "";
            }
        }

        @JsonAnySetter
        public void unknownKey(String key, Object ignoredValue) {
            unknownKeys.add(key);
        }

        @Override
        public String toString() {
            return "IssueRequest[keys=" + unknownKeys.size() + "]";
        }
    }

    /** Contract C1: the policy header, built from an allow-list. Money is a decimal string (NFR-01). */
    public record PolicyHeaderResponse(
            String policyNumber,
            String applicationId,
            String productCode,
            String status,
            int currentVersionNo,
            int ruleVersion,
            LocalDate effectiveDate,
            LocalDate expiryDate,
            String sumInsured,
            String annualPremium,
            LocalDate nextPremiumDueDate,
            int endorsementCount) {

        static PolicyHeaderResponse from(PolicyHeader header) {
            return new PolicyHeaderResponse(
                    header.policyNumber(),
                    header.applicationId(),
                    header.productCode(),
                    header.status().name(),
                    header.currentVersionNo(),
                    header.ruleVersion(),
                    header.effectiveDate(),
                    header.expiryDate(),
                    header.sumInsured().toPlainString(),
                    header.annualPremium().toPlainString(),
                    header.nextPremiumDueDate(),
                    header.endorsementCount());
        }
    }

    /** Contract C2: one entry of the caller's policy list. */
    public record PolicySummaryResponse(
            String policyNumber,
            String productCode,
            String status,
            String sumInsured,
            String annualPremium,
            LocalDate nextPremiumDueDate,
            int endorsementCount) {

        static PolicySummaryResponse from(PolicySummary summary) {
            return new PolicySummaryResponse(
                    summary.policyNumber(),
                    summary.productCode(),
                    summary.status().name(),
                    summary.sumInsured().toPlainString(),
                    summary.annualPremium().toPlainString(),
                    summary.nextPremiumDueDate(),
                    summary.endorsementCount());
        }
    }

    /**
     * Contract C3, a version. The risk inputs are the product's declared inputs minus the health inputs (P-9). The
     * address and the nominees are personal data, so the string form hides them (NFR-03).
     */
    public record VersionResponse(
            int versionNo,
            LocalDate effectiveDate,
            LocalDate expiryDate,
            String sumInsured,
            String annualPremium,
            int ruleVersion,
            Map<String, Object> riskInputs,
            Address address,
            List<Nominee> nominees,
            Instant createdAt) {

        static VersionResponse from(PolicyDetail.VersionView version) {
            return new VersionResponse(
                    version.versionNo(),
                    version.effectiveDate(),
                    version.expiryDate(),
                    version.sumInsured().toPlainString(),
                    version.annualPremium().toPlainString(),
                    version.ruleVersion(),
                    version.riskInputs(),
                    version.address(),
                    version.nominees(),
                    version.createdAt());
        }

        @Override
        public String toString() {
            return "VersionResponse[versionNo=" + versionNo + "]";
        }
    }

    /**
     * Contract C3, an endorsement. {@code changes} is built from an allow-list per type (P-9) and carries an address or
     * a nominee, so the string form hides it (NFR-03).
     */
    public record EndorsementResponse(
            int sequenceNo,
            String type,
            Map<String, Object> changes,
            int beforeVersionNo,
            int afterVersionNo,
            String newAnnualPremium,
            String adjustment,
            LocalDate effectiveDate,
            String actorId,
            Instant createdAt) {

        static EndorsementResponse from(PolicyDetail.EndorsementView endorsement) {
            return new EndorsementResponse(
                    endorsement.sequenceNo(),
                    endorsement.type(),
                    endorsement.changes(),
                    endorsement.beforeVersionNo(),
                    endorsement.afterVersionNo(),
                    endorsement.newAnnualPremium().toPlainString(),
                    endorsement.adjustment().toPlainString(),
                    endorsement.effectiveDate(),
                    endorsement.actorId(),
                    endorsement.createdAt());
        }

        @Override
        public String toString() {
            return "EndorsementResponse[sequenceNo=" + sequenceNo + "]";
        }
    }

    /** Contract C3, a status transition; {@code fromStatus} is null for the move that created the policy. */
    public record TransitionResponse(String fromStatus, String toStatus, String reason, String actorId, Instant at) {

        static TransitionResponse from(PolicyDetail.TransitionView transition) {
            return new TransitionResponse(
                    transition.fromStatus() == null ? null : transition.fromStatus().name(),
                    transition.toStatus().name(),
                    transition.reason(),
                    transition.actorId(),
                    transition.at());
        }
    }

    /** Contract C3, a term. */
    public record TermResponse(int termNo, LocalDate startDate, LocalDate endDate, String premium, boolean paid) {

        static TermResponse from(PolicyDetail.TermView term) {
            return new TermResponse(term.termNo(), term.startDate(), term.endDate(), term.premium().toPlainString(),
                    term.paid());
        }
    }

    /** Contract C3. Refunds are always empty until cancellation arrives (P-9). */
    public record PolicyDetailResponse(
            PolicyHeaderResponse policy,
            List<VersionResponse> versions,
            List<EndorsementResponse> endorsements,
            List<TransitionResponse> transitions,
            List<TermResponse> terms,
            List<Object> refunds) {

        static PolicyDetailResponse from(PolicyDetail detail) {
            return new PolicyDetailResponse(
                    PolicyHeaderResponse.from(detail.policy()),
                    detail.versions().stream().map(VersionResponse::from).toList(),
                    detail.endorsements().stream().map(EndorsementResponse::from).toList(),
                    detail.transitions().stream().map(TransitionResponse::from).toList(),
                    detail.terms().stream().map(TermResponse::from).toList(),
                    List.of());
        }

        /** The policy number only: the versions carry the insured address (NFR-03). */
        @Override
        public String toString() {
            return "PolicyDetailResponse[policyNumber=" + policy.policyNumber() + "]";
        }
    }

    private final PolicyIssuanceService issuance;
    private final PolicyQueryService queries;

    public PolicyController(PolicyIssuanceService issuance, PolicyQueryService queries) {
        this.issuance = issuance;
        this.queries = queries;
    }

    /** CUSTOMER only. JSON only (NFR-04.3). Bodies over 16 KB are refused before this point with 413 (NFR-04.4). */
    @PostMapping(path = "/applications/{applicationId}/issue", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public PolicyHeaderResponse issue(@PathVariable("applicationId") String applicationId,
            @RequestBody IssueRequest request, Authentication authentication) {
        return PolicyHeaderResponse.from(issuance.issue(new IssuePolicyCommand(authentication.getName(),
                applicationId, request.effectiveDate, List.copyOf(request.unknownKeys))));
    }

    /** CUSTOMER only: the caller's own policies, newest first (C2). */
    @GetMapping("/policies/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<PolicySummaryResponse> mine(Authentication authentication) {
        return queries.mine(authentication.getName()).stream().map(PolicySummaryResponse::from).toList();
    }

    /** The owning CUSTOMER or an ADMIN (C3). Another customer's policy is a 404, never a 403. */
    @GetMapping("/policies/{policyNumber}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public PolicyDetailResponse detail(@PathVariable("policyNumber") String policyNumber,
            Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
        return PolicyDetailResponse.from(queries.detail(policyNumber, authentication.getName(), admin));
    }
}
