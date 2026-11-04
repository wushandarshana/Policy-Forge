package com.horizon.policyforge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizon.policyforge.domain.endorsement.Nominee;
import com.horizon.policyforge.domain.rating.RiskInputSchema;
import com.horizon.policyforge.repository.EndorsementEntity;
import com.horizon.policyforge.repository.EndorsementRepository;
import com.horizon.policyforge.repository.PolicyEntity;
import com.horizon.policyforge.repository.PolicyRepository;
import com.horizon.policyforge.repository.PolicyStatusTransitionRepository;
import com.horizon.policyforge.repository.PolicyTermEntity;
import com.horizon.policyforge.repository.PolicyTermRepository;
import com.horizon.policyforge.repository.PolicyVersionEntity;
import com.horizon.policyforge.repository.PolicyVersionRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The policy read use cases (AC-05.1): a customer's own policies (C2) and one policy's detail (C3). A customer reads
 * only their own policies and an ADMIN reads any; everyone else gets the same not-found answer as for an unknown number.
 * Every view is built from an allow-list (P-9): the risk inputs of a version are the product's declared inputs minus
 * the health inputs, and no KYC field is ever carried.
 */
@Service
public class PolicyQueryService {

    /** The shape of a number this application issues. Anything else is not found, without a lookup. */
    static final Pattern POLICY_NUMBER = Pattern.compile("PF-(TL|MO|HH)-[0-9]{4}-[0-9]{6}");

    private static final TypeReference<LinkedHashMap<String, Object>> INPUTS = new TypeReference<>() {
    };
    private static final TypeReference<List<Nominee>> NOMINEES = new TypeReference<>() {
    };

    private final PolicyRepository policies;
    private final PolicyVersionRepository versions;
    private final PolicyTermRepository terms;
    private final PolicyStatusTransitionRepository transitions;
    private final EndorsementRepository endorsements;
    private final EndorsementViews endorsementViews;
    private final RuleSetService ruleSets;
    private final PolicyTerms policyTerms;
    private final ObjectMapper mapper;

    public PolicyQueryService(PolicyRepository policies, PolicyVersionRepository versions, PolicyTermRepository terms,
            PolicyStatusTransitionRepository transitions, EndorsementRepository endorsements,
            EndorsementViews endorsementViews, RuleSetService ruleSets, PolicyTerms policyTerms,
            ObjectMapper mapper) {
        this.policies = policies;
        this.versions = versions;
        this.terms = terms;
        this.transitions = transitions;
        this.endorsements = endorsements;
        this.endorsementViews = endorsementViews;
        this.ruleSets = ruleSets;
        this.policyTerms = policyTerms;
        this.mapper = mapper;
    }

    /** The customer's own policies, newest first. Never another customer's. */
    @Transactional(readOnly = true)
    public List<PolicySummary> mine(String customerId) {
        List<PolicyEntity> own = policies.findByCustomerIdOrderByIssuedAtDescIdDesc(customerId);
        if (own.isEmpty()) {
            return List.of();
        }
        List<String> numbers = own.stream().map(PolicyEntity::getPolicyNumber).toList();
        Map<String, List<PolicyVersionEntity>> versionsByPolicy = versions.findByPolicyNumberIn(numbers).stream()
                .collect(Collectors.groupingBy(PolicyVersionEntity::getPolicyNumber));
        Map<String, List<PolicyTermEntity>> termsByPolicy = terms.findByPolicyNumberIn(numbers).stream()
                .collect(Collectors.groupingBy(PolicyTermEntity::getPolicyNumber));
        Map<String, Long> endorsementCounts = endorsements.findByPolicyNumberIn(numbers).stream()
                .collect(Collectors.groupingBy(EndorsementEntity::getPolicyNumber, Collectors.counting()));
        return own.stream()
                .map(policy -> PolicySummary.from(header(policy, versionsByPolicy.get(policy.getPolicyNumber()),
                        termsByPolicy.get(policy.getPolicyNumber()),
                        endorsementCounts.getOrDefault(policy.getPolicyNumber(), 0L).intValue())))
                .toList();
    }

    /**
     * One policy with its versions, transitions and terms.
     *
     * @param admin whether the caller is an ADMIN, who may read any policy
     * @throws ResourceNotFoundException (404) for an unknown or malformed number, or another customer's policy
     */
    @Transactional(readOnly = true)
    public PolicyDetail detail(String policyNumber, String actorId, boolean admin) {
        if (policyNumber == null || !POLICY_NUMBER.matcher(policyNumber).matches()) {
            throw new ResourceNotFoundException("Policy not found");
        }
        PolicyEntity policy = policies.findByPolicyNumber(policyNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found"));
        if (!admin && !policy.getCustomerId().equals(actorId)) {
            throw new ResourceNotFoundException("Policy not found");
        }

        List<PolicyVersionEntity> allVersions = versions.findByPolicyNumberOrderByVersionNoAsc(policyNumber);
        List<PolicyTermEntity> allTerms = terms.findByPolicyNumberOrderByTermNoAsc(policyNumber);
        List<EndorsementEntity> endorsementEntities =
                endorsements.findByPolicyNumberOrderBySequenceNoAsc(policyNumber);
        Map<Integer, RiskInputSchema> schemas = new HashMap<>();
        List<PolicyDetail.VersionView> versionViews = allVersions.stream()
                .map(version -> versionView(policy, version, schemas))
                .toList();
        List<PolicyDetail.TransitionView> transitionViews = transitions.findByPolicyNumberOrderByIdAsc(policyNumber)
                .stream()
                .map(t -> new PolicyDetail.TransitionView(t.getFromStatus(), t.getToStatus(), t.getReason(),
                        t.getActorId(), t.getOccurredAt()))
                .toList();
        List<PolicyDetail.TermView> termViews = allTerms.stream()
                .map(t -> new PolicyDetail.TermView(t.getTermNo(), t.getStartDate(), t.getEndDate(), t.getPremium(),
                        t.isPaid(), t.getRuleVersion()))
                .toList();
        List<PolicyDetail.EndorsementView> endorsementViewList = endorsementEntities.stream()
                .map(endorsementViews::view)
                .toList();
        return new PolicyDetail(header(policy, allVersions, allTerms, endorsementEntities.size()), versionViews,
                endorsementViewList, transitionViews, termViews, pendingRenewal(policy, allTerms));
    }

    /**
     * The renewal offer an in-force policy can still pay (C5): the unpaid term above the current paid term, with
     * {@code payableUntil} = the current paid term's end + the grace days of that term's own rule version. A policy that
     * is not in force (LAPSED, CANCELLED) has no offer left to pay, so it shows none; its unpaid term stays in the
     * term history.
     */
    private PolicyDetail.PendingRenewalView pendingRenewal(PolicyEntity policy, List<PolicyTermEntity> allTerms) {
        if (!policy.getStatus().isInForce()) {
            return null;
        }
        Optional<PolicyTermEntity> pending = policyTerms.pending(allTerms);
        if (pending.isEmpty()) {
            return null;
        }
        PolicyTermEntity current = policyTerms.requireCurrentPaid(allTerms, policy.getPolicyNumber());
        int graceDays = ruleSets.version(policy.getProductCode(), current.getRuleVersion()).lifecycle()
                .gracePeriodDays();
        PolicyTermEntity offer = pending.get();
        return new PolicyDetail.PendingRenewalView(offer.getTermNo(), offer.getStartDate(), offer.getEndDate(),
                offer.getPremium(), offer.getRuleVersion(), policyTerms.payableUntil(current, graceDays));
    }

    /**
     * The header from the policy's current version and its current paid term (plan G-5), with its endorsement count. An
     * unpaid renewal offer does not move the header's dates: they follow the term in force until the renewal is paid.
     */
    private PolicyHeader header(PolicyEntity policy, Collection<PolicyVersionEntity> policyVersions,
            Collection<PolicyTermEntity> termRows, int endorsementCount) {
        PolicyVersionEntity current = policyVersions.stream()
                .filter(version -> version.getVersionNo() == policy.getCurrentVersionNo())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Policy has no current version: policyNumber="
                        + policy.getPolicyNumber()));
        PolicyTermEntity term = policyTerms.requireCurrentPaid(termRows, policy.getPolicyNumber());
        return PolicyHeader.of(policy, current, term, endorsementCount);
    }

    private PolicyDetail.VersionView versionView(PolicyEntity policy, PolicyVersionEntity version,
            Map<Integer, RiskInputSchema> schemas) {
        RiskInputSchema schema = schemas.computeIfAbsent(version.getRuleVersion(), ruleVersion -> RiskInputSchema
                .forModel(ruleSets.version(policy.getProductCode(), ruleVersion).model()));
        // Allow-list (P-9, NFR-04.2): only the declared, non-health inputs; the stored snapshot keeps the rest.
        Map<String, Object> riskInputs = schema.publicView(read(version.getRiskInputsJson(), INPUTS));
        return new PolicyDetail.VersionView(version.getVersionNo(), version.getEffectiveDate(),
                version.getExpiryDate(), version.getSumInsured(), version.getAnnualPremium(), version.getRuleVersion(),
                riskInputs, version.getAddress(), read(version.getNomineesJson(), NOMINEES), version.getCreatedAt());
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored policy data cannot be read", e);
        }
    }
}
