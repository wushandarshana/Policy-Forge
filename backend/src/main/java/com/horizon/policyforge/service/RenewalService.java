package com.horizon.policyforge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizon.policyforge.domain.EchoedKeySanitiser;
import com.horizon.policyforge.domain.policy.InvalidPolicyStateException;
import com.horizon.policyforge.domain.policy.PolicyStateMachine;
import com.horizon.policyforge.domain.policy.PolicyStatus;
import com.horizon.policyforge.domain.rating.InvalidRiskInputException;
import com.horizon.policyforge.domain.rating.PremiumBreakdown;
import com.horizon.policyforge.domain.rating.PremiumCalculator;
import com.horizon.policyforge.domain.renewal.GracePeriodPolicy;
import com.horizon.policyforge.domain.renewal.RenewalDates;
import com.horizon.policyforge.domain.renewal.RenewalPolicy;
import com.horizon.policyforge.domain.ruleset.Lifecycle;
import com.horizon.policyforge.domain.ruleset.RuleSetVersion;
import com.horizon.policyforge.repository.EndorsementRepository;
import com.horizon.policyforge.repository.PolicyEntity;
import com.horizon.policyforge.repository.PolicyRepository;
import com.horizon.policyforge.repository.PolicyStatusTransitionEntity;
import com.horizon.policyforge.repository.PolicyStatusTransitionRepository;
import com.horizon.policyforge.repository.PolicyTermEntity;
import com.horizon.policyforge.repository.PolicyTermRepository;
import com.horizon.policyforge.repository.PolicyVersionEntity;
import com.horizon.policyforge.repository.PolicyVersionRepository;
import com.horizon.policyforge.service.ValidationFailedException.FieldViolation;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The renewal use cases (AC-07): the renewal cycle and the owner's renewal payment. Every business decision is the
 * domain's ({@link RenewalPolicy}, {@link GracePeriodPolicy}, {@link PremiumCalculator}, {@link PolicyStateMachine}
 * through the policy aggregate). This class loads, delegates, appends and audits.
 *
 * <p><b>The cycle</b> (P-7) runs for one date, from the admin job or from {@link RenewalScheduler}. Each in-force policy
 * is processed in its own transaction, first the offer and then the lapse, so one failure undoes nothing of the others
 * and a policy whose offer window was missed still lapses in the same run. The offer is a new unpaid term that starts the
 * day after the current paid term ends, rated with {@link PremiumCalculator} over the current version's stored risk
 * inputs and with the rule version active on the run date, which the term records. An offer writes a term and no
 * transition. The lapse goes through the policy aggregate, so {@code PolicyStateMachine} decides, and appends the
 * transition with reason {@code GRACE_PERIOD_EXPIRED} and actor {@code system:renewal-scheduler}, even when an admin
 * triggered the run. Two concurrent runs for one date leave one term (the unique key refuses the second) and at most one
 * lapse (the policy's optimistic lock refuses the second), and the loser skips the policy. Each run appends one
 * {@code RENEWAL_CYCLE_RUN} audit entry (P-10).
 *
 * <p><b>The payment</b> (G-3, P-5) checks in this order: the body shape (400), the policy and its owner (404), the state
 * (409 invalid policy state, because the state machine refuses RENEWED), a pending term (409 {@code NO_PENDING_RENEWAL}),
 * and the window (409 {@code RENEWAL_PAYMENT_WINDOW_CLOSED}). In one transaction the pending term is marked paid, policy
 * version n + 1 is appended with the renewal term's premium, rule version and dates, and the policy moves to RENEWED with
 * reason {@code RENEWAL_PAID}. A payment that races a lapse, a cancel or another payment loses on the policy's optimistic
 * lock (or the unique version key) and answers 409 {@code POLICY_UPDATE_CONFLICT} with nothing written (P-8).
 *
 * <p>It logs policy numbers, counts and rule versions only (NFR-03): the stored risk inputs, the address and the
 * nominees are never passed to a logger.
 */
@Service
public class RenewalService {

    private static final Logger LOG = LoggerFactory.getLogger(RenewalService.class);

    /** The actor of the scheduler and of every lapse, even one an admin triggered (P-7). */
    public static final String SYSTEM_ACTOR = "system:renewal-scheduler";

    static final String CYCLE_ACTION = "RENEWAL_CYCLE_RUN";
    static final String CYCLE_ENTITY_TYPE = "RENEWAL_CYCLE";
    static final String LAPSE_REASON = "GRACE_PERIOD_EXPIRED";
    static final String PAID_REASON = "RENEWAL_PAID";

    /**
     * Lifts the admin job's date bound (AC-07.8). Off by default; only the test and browser-test configurations turn
     * it on, never production or the packaged demo.
     */
    public static final String ANY_DATE_PROPERTY = "policyforge.jobs.renewal-cycle.any-date";
    private static final String JOB_DATE_RULE = "must be a date in the format YYYY-MM-DD from "
            + RenewalPolicy.JOB_DATE_MAX_DAYS_BACK + " days ago up to today";

    private static final TypeReference<LinkedHashMap<String, Object>> INPUTS = new TypeReference<>() {
    };
    private static final List<String> IN_FORCE_STATUSES = Arrays.stream(PolicyStatus.values())
            .filter(PolicyStatus::isInForce).map(PolicyStatus::name).toList();

    /** What processing one policy did. */
    private record Outcome(boolean offered, boolean lapsed) {
        static final Outcome NOTHING = new Outcome(false, false);
    }

    private final PolicyRepository policies;
    private final PolicyVersionRepository versions;
    private final PolicyTermRepository terms;
    private final PolicyStatusTransitionRepository transitions;
    private final EndorsementRepository endorsements;
    private final RuleSetService ruleSets;
    private final PremiumCalculator calculator;
    private final RenewalPolicy renewalPolicy;
    private final GracePeriodPolicy gracePolicy;
    private final PolicyTerms policyTerms;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final PolicyStateMachine stateMachine = new PolicyStateMachine();
    private final boolean anyJobDate;

    public RenewalService(PolicyRepository policies, PolicyVersionRepository versions, PolicyTermRepository terms,
            PolicyStatusTransitionRepository transitions, EndorsementRepository endorsements, RuleSetService ruleSets,
            PremiumCalculator calculator, RenewalPolicy renewalPolicy, GracePeriodPolicy gracePolicy,
            PolicyTerms policyTerms, AuditService audit, ObjectMapper mapper, Clock clock,
            PlatformTransactionManager transactionManager,
            @Value("${" + ANY_DATE_PROPERTY + ":false}") boolean anyJobDate) {
        this.policies = policies;
        this.versions = versions;
        this.terms = terms;
        this.transitions = transitions;
        this.endorsements = endorsements;
        this.ruleSets = ruleSets;
        this.calculator = calculator;
        this.renewalPolicy = renewalPolicy;
        this.gracePolicy = gracePolicy;
        this.policyTerms = policyTerms;
        this.audit = audit;
        this.mapper = mapper;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
        this.anyJobDate = anyJobDate;
    }

    // ---- the cycle ----

    /**
     * The admin job (contract C1): validates the request, then runs the cycle for the date (default today) as the admin.
     * Unless {@link #ANY_DATE_PROPERTY} is on, the date must satisfy {@code today - 30 <= date <= today} (AC-07.8).
     *
     * @throws ValidationFailedException (400) for an unknown body key, or a malformed or out-of-bound {@code date},
     *                                   reported together; nothing runs
     */
    public RenewalCycleResult runCycle(RunCycleCommand command) {
        List<FieldViolation> problems = new ArrayList<>();
        command.unknownKeys().stream().map(EchoedKeySanitiser::sanitise).distinct()
                .forEach(key -> problems.add(new FieldViolation(key, "is not an accepted field")));
        LocalDate today = LocalDate.now(clock);
        LocalDate runDate = today;
        if (command.date() != null) {
            try {
                runDate = LocalDate.parse(command.date());
                if (!anyJobDate && !renewalPolicy.isJobDateAllowed(runDate, today)) {
                    problems.add(new FieldViolation("date", JOB_DATE_RULE));
                }
            } catch (DateTimeParseException unreadable) {
                problems.add(new FieldViolation("date", "must be a date in the format YYYY-MM-DD"));
            }
        }
        if (!problems.isEmpty()) {
            throw new ValidationFailedException(problems);
        }
        return runCycle(runDate, command.actorId());
    }

    /**
     * Runs the cycle for {@code runDate}: every in-force policy, ascending by number, each in its own transaction, then
     * one audit entry for the run. Idempotent for a date: a second run creates no term and lapses nothing again.
     *
     * @param actorId the admin, or {@link #SYSTEM_ACTOR} for the schedule; recorded on the audit entry
     */
    public RenewalCycleResult runCycle(LocalDate runDate, String actorId) {
        List<String> offered = new ArrayList<>();
        List<String> lapsed = new ArrayList<>();
        for (PolicyEntity inForce : policies.findByStatusInOrderByPolicyNumberAsc(IN_FORCE_STATUSES)) {
            String number = inForce.getPolicyNumber();
            try {
                Outcome outcome = transaction.execute(status -> process(number, runDate));
                if (outcome != null && outcome.offered()) {
                    offered.add(number);
                }
                if (outcome != null && outcome.lapsed()) {
                    lapsed.add(number);
                }
            } catch (ConcurrencyFailureException | DataIntegrityViolationException lostRace) {
                // Another run or request changed this policy first: its work stands, nothing of ours was kept.
                LOG.info("Renewal cycle skipped a policy changed concurrently policyNumber={} runDate={}", number,
                        runDate);
            } catch (RuntimeException failure) {
                // One policy's failure never undoes the others (P-7). The message is ours or the database's: no PII.
                LOG.error("Renewal cycle failed for a policy policyNumber={} runDate={}", number, runDate, failure);
            }
        }
        audit.record(actorId, CYCLE_ACTION, CYCLE_ENTITY_TYPE, runDate.toString());
        LOG.info("Renewal cycle run runDate={} offersCreated={} lapsed={}", runDate, offered.size(), lapsed.size());
        return new RenewalCycleResult(runDate, offered, lapsed);
    }

    /** One policy in its own transaction: the offer (step 1), then the lapse (step 2). */
    private Outcome process(String policyNumber, LocalDate runDate) {
        PolicyEntity policy = policies.findByPolicyNumber(policyNumber).orElse(null);
        if (policy == null || !policy.getStatus().isInForce()) {
            return Outcome.NOTHING;
        }
        List<PolicyTermEntity> policyTermRows = new ArrayList<>(terms.findByPolicyNumberOrderByTermNoAsc(policyNumber));
        PolicyTermEntity current = policyTerms.currentPaid(policyTermRows).orElse(null);
        if (current == null) {
            return Outcome.NOTHING;
        }
        // The timing and the grace come from the rule version of the current paid term, which the policy is bound by.
        Lifecycle lifecycle = ruleSets.version(policy.getProductCode(), current.getRuleVersion()).lifecycle();

        boolean offered = false;
        if (renewalPolicy.isOfferDue(policyTerms.periods(policyTermRows), lifecycle.renewalOfferDaysBeforeExpiry(),
                runDate)) {
            Optional<PolicyTermEntity> offer = createOffer(policy, current, policyTermRows, runDate);
            if (offer.isPresent()) {
                policyTermRows.add(offer.get());
                offered = true;
            }
        }

        boolean lapsed = false;
        Optional<PolicyTermEntity> pending = policyTerms.pending(policyTermRows);
        if (pending.isPresent() && gracePolicy.isLapsed(current.getEndDate(), lifecycle.gracePeriodDays(), runDate,
                pending.get().isPaid())) {
            lapse(policy);
            lapsed = true;
        }
        return new Outcome(offered, lapsed);
    }

    /**
     * The renewal term (AC-07.1, AC-07.3): premium from the rule version active on the run date, re-rating the current
     * version's stored risk inputs unchanged (SG-3). A policy the active version cannot rate gets no offer (SG-7).
     */
    private Optional<PolicyTermEntity> createOffer(PolicyEntity policy, PolicyTermEntity current,
            List<PolicyTermEntity> existing, LocalDate runDate) {
        String number = policy.getPolicyNumber();
        RuleSetVersion active;
        try {
            active = ruleSets.activeVersion(policy.getProductCode(), runDate);
        } catch (NoActiveRuleSetException none) {
            LOG.warn("Renewal offer skipped, no active rule set policyNumber={} runDate={}", number, runDate);
            return Optional.empty();
        }
        PolicyVersionEntity currentVersion = versions
                .findByPolicyNumberAndVersionNo(number, policy.getCurrentVersionNo())
                .orElseThrow(() -> new IllegalStateException("Policy has no current version: policyNumber=" + number));
        PremiumBreakdown priced;
        try {
            priced = calculator.calculate(active, read(currentVersion.getRiskInputsJson()));
        } catch (InvalidRiskInputException rejected) {
            LOG.warn("Renewal offer skipped, the active rule version rejects the stored inputs policyNumber={}"
                    + " ruleVersion={}", number, active.version());
            return Optional.empty();
        }
        RenewalDates dates = renewalPolicy.renewalDates(current.getEndDate(), active.lifecycle().termMonths());
        int termNo = existing.stream().mapToInt(PolicyTermEntity::getTermNo).max().orElse(0) + 1;
        PolicyTermEntity offer = terms.save(PolicyTermEntity.of(number, termNo, dates.startDate(), dates.endDate(),
                priced.annualPremium(), false, active.version()));
        // MINOR-2: the offer takes the policy's optimistic lock. A cancel, endorsement or payment of the same policy that
        // committed since the policy was read makes this fail (the cycle skips the policy and keeps no term), and one
        // still in flight fails later on the bumped version. No unpaid term is left on a policy cancelled meanwhile.
        policies.bumpVersion(policy);
        LOG.info("Renewal offered policyNumber={} termNo={} ruleVersion={}", number, termNo, active.version());
        return Optional.of(offer);
    }

    /** Lapses the policy through the aggregate (and so the state machine) and appends the transition. */
    private void lapse(PolicyEntity policy) {
        PolicyStatus from = policy.getStatus();
        policy.applyTransition(PolicyStatus.LAPSED, policy.getCurrentVersionNo());
        policies.saveAndFlush(policy);
        transitions.save(PolicyStatusTransitionEntity.of(policy.getPolicyNumber(), from, PolicyStatus.LAPSED,
                LAPSE_REASON, SYSTEM_ACTOR, Instant.now(clock)));
        LOG.info("Policy lapsed policyNumber={}", policy.getPolicyNumber());
    }

    // ---- the payment ----

    /**
     * Pays the pending renewal term of the customer's policy.
     *
     * @throws ValidationFailedException   (400) for any body key
     * @throws ResourceNotFoundException   (404) for an unknown or malformed number, or another customer's policy
     * @throws InvalidPolicyStateException (409) when the policy is not in force
     * @throws ConflictException           (409) {@code NO_PENDING_RENEWAL}, {@code RENEWAL_PAYMENT_WINDOW_CLOSED}, or
     *                                     {@code POLICY_UPDATE_CONFLICT} when a concurrent request won
     */
    @Transactional
    public RenewalPayment pay(PayRenewalCommand command) {
        // P-5 step 1: the body shape, before anything is looked up.
        List<FieldViolation> problems = new ArrayList<>();
        command.unknownKeys().stream().map(EchoedKeySanitiser::sanitise).distinct()
                .forEach(key -> problems.add(new FieldViolation(key, "is not an accepted field")));
        if (!problems.isEmpty()) {
            throw new ValidationFailedException(problems);
        }

        // P-5 step 2: the policy and its owner. Another customer's policy looks the same as an unknown one.
        PolicyEntity policy = loadOwned(command);
        String number = policy.getPolicyNumber();

        // P-5 step 3: the state. The state machine refuses RENEWED for a LAPSED or CANCELLED policy.
        stateMachine.transition(policy.getStatus(), PolicyStatus.RENEWED);

        // P-5 steps 4 and 5: a pending term, then the payment window.
        List<PolicyTermEntity> allTerms = terms.findByPolicyNumberOrderByTermNoAsc(number);
        PolicyTermEntity current = policyTerms.requireCurrentPaid(allTerms, number);
        PolicyTermEntity pending = policyTerms.pending(allTerms)
                .orElseThrow(() -> new ConflictException(ConflictException.NO_PENDING_RENEWAL));
        int graceDays = ruleSets.version(policy.getProductCode(), current.getRuleVersion()).lifecycle()
                .gracePeriodDays();
        if (!renewalPolicy.isPaymentAllowed(policyTerms.periods(allTerms), graceDays, LocalDate.now(clock))) {
            throw new ConflictException(ConflictException.RENEWAL_PAYMENT_WINDOW_CLOSED);
        }

        PolicyVersionEntity currentVersion = versions
                .findByPolicyNumberAndVersionNo(number, policy.getCurrentVersionNo())
                .orElseThrow(() -> new IllegalStateException("Policy has no current version: policyNumber=" + number));
        int endorsementCount = endorsements.findFirstByPolicyNumberOrderBySequenceNoDesc(number)
                .map(last -> last.getSequenceNo()).orElse(0);
        Instant now = Instant.now(clock);
        int newVersionNo = policy.getCurrentVersionNo() + 1;
        PolicyStatus from = policy.getStatus();

        // G-3: the term is paid, version n + 1 is appended and the policy moves to RENEWED, all or nothing.
        PolicyVersionEntity version;
        try {
            pending.markPaid();
            version = versions.save(PolicyVersionEntity.of(number, newVersionNo, currentVersion.getSumInsured(),
                    pending.getPremium(), currentVersion.getRiskInputsJson(), currentVersion.getAddress(),
                    currentVersion.getNomineesJson(), pending.getRuleVersion(), pending.getStartDate(),
                    pending.getEndDate(), now));
            policy.applyTransition(PolicyStatus.RENEWED, newVersionNo);
            policies.saveAndFlush(policy);
            transitions.save(PolicyStatusTransitionEntity.of(number, from, PolicyStatus.RENEWED, PAID_REASON,
                    command.customerId(), now));
        } catch (ConcurrencyFailureException lostRace) {
            throw new ConflictException(ConflictException.POLICY_UPDATE_CONFLICT);
        } catch (DataIntegrityViolationException failure) {
            if (WriteConflicts.isUniqueViolation(failure)) {
                throw new ConflictException(ConflictException.POLICY_UPDATE_CONFLICT);
            }
            throw failure;
        }

        LOG.info("Renewal paid policyNumber={} termNo={} versionNo={}", number, pending.getTermNo(), newVersionNo);
        return new RenewalPayment(PolicyHeader.of(policy, version, pending, endorsementCount),
                new PolicyDetail.TermView(pending.getTermNo(), pending.getStartDate(), pending.getEndDate(),
                        pending.getPremium(), pending.isPaid(), pending.getRuleVersion()));
    }

    /** P-5 step 2: an unknown, malformed or another customer's policy all look the same: 404. */
    private PolicyEntity loadOwned(PayRenewalCommand command) {
        String number = command.policyNumber();
        if (number == null || !PolicyQueryService.POLICY_NUMBER.matcher(number).matches()) {
            throw new ResourceNotFoundException("Policy not found");
        }
        PolicyEntity policy = policies.findByPolicyNumber(number)
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found"));
        if (!policy.getCustomerId().equals(command.customerId())) {
            throw new ResourceNotFoundException("Policy not found");
        }
        return policy;
    }

    private Map<String, Object> read(String json) {
        try {
            return mapper.readValue(json, INPUTS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored policy data cannot be read", e);
        }
    }
}
