package com.horizon.policyforge.service;

import com.horizon.policyforge.domain.EchoedKeySanitiser;
import com.horizon.policyforge.domain.cancellation.CancellationDateRule;
import com.horizon.policyforge.domain.cancellation.CancellationReason;
import com.horizon.policyforge.domain.cancellation.DatedAdjustment;
import com.horizon.policyforge.domain.cancellation.InvalidCancellationDateException;
import com.horizon.policyforge.domain.cancellation.ProRataRefundCalculator;
import com.horizon.policyforge.domain.cancellation.RefundPremiumBasis;
import com.horizon.policyforge.domain.cancellation.RefundQuote;
import com.horizon.policyforge.domain.policy.InvalidPolicyStateException;
import com.horizon.policyforge.domain.policy.PolicyStateMachine;
import com.horizon.policyforge.domain.policy.PolicyStatus;
import com.horizon.policyforge.domain.ruleset.Lifecycle;
import com.horizon.policyforge.repository.EndorsementEntity;
import com.horizon.policyforge.repository.EndorsementRepository;
import com.horizon.policyforge.repository.PolicyEntity;
import com.horizon.policyforge.repository.PolicyRepository;
import com.horizon.policyforge.repository.PolicyStatusTransitionEntity;
import com.horizon.policyforge.repository.PolicyStatusTransitionRepository;
import com.horizon.policyforge.repository.PolicyTermEntity;
import com.horizon.policyforge.repository.PolicyTermRepository;
import com.horizon.policyforge.repository.PolicyVersionEntity;
import com.horizon.policyforge.repository.PolicyVersionRepository;
import com.horizon.policyforge.repository.RefundRecordEntity;
import com.horizon.policyforge.repository.RefundRecordRepository;
import com.horizon.policyforge.service.ValidationFailedException.FieldViolation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The cancellation use cases (AC-08): preview the refund of cancelling an in-force policy, and cancel it. Every business
 * decision is the domain's: {@link PolicyStateMachine} (the move to CANCELLED), {@link CancellationDateRule} (the date
 * lies in the current paid term), {@link RefundPremiumBasis} (the premium the refund is calculated on),
 * {@link ProRataRefundCalculator} (the figures) and {@link CancellationReason}. This class loads, delegates, appends and
 * logs.
 *
 * <p>The checks run in the P-5 order. The cancel: the body shape (400: unknown keys, a malformed date, a missing or
 * unknown reason, all reported together), then the policy and its owner (404), then the state (409 invalid policy state,
 * because the state machine refuses CANCELLED for a LAPSED or CANCELLED policy), then the date window (400
 * {@code CANCELLATION_DATE_OUT_OF_TERM} naming {@code cancellationDate}). The preview: the {@code date} format (400),
 * then 404, then 409, then the date window naming {@code date}. Both use the same preparation, so the preview shows
 * exactly what the cancel would write.
 *
 * <p>The current paid term (plan G-5) is the term the date must lie in and the term the refund is calculated on: term 1
 * until a renewal is paid, and the renewal term from the moment it is paid. The fee and the cooling-off period are those of
 * that term's own rule version, and only the first term has a cooling-off period.
 *
 * <p>{@link #cancel} writes in one transaction, in this order: the refund record, then the policy's move to CANCELLED
 * through the aggregate (and so the state machine) and the {@code -> CANCELLED} transition row. If any step fails nothing
 * is kept (AC-08.4). Two concurrent cancels of one policy both insert the refund record, so the unique policy key and the
 * policy's optimistic lock let one win and make the other fail, and that one answers 409 {@code POLICY_UPDATE_CONFLICT}
 * with nothing written (P-8). A cancellation is recorded by its transition (actor, reason, time), not by an audit entry
 * (P-10).
 *
 * <p>It logs the policy number, the term number and whether cooling-off applied only (NFR-03).
 */
@Service
public class CancellationService {

    private static final Logger LOG = LoggerFactory.getLogger(CancellationService.class);

    static final String DATE_FIELD = "date";
    static final String CANCELLATION_DATE_FIELD = "cancellationDate";
    static final String REASON_FIELD = "reason";
    private static final String NOT_A_DATE = "must be a date in the format YYYY-MM-DD";
    private static final String NOT_A_REASON = "must be one of " + Arrays.stream(CancellationReason.values())
            .map(Enum::name).collect(Collectors.joining(", "));

    private final PolicyRepository policies;
    private final PolicyVersionRepository versions;
    private final PolicyTermRepository terms;
    private final PolicyStatusTransitionRepository transitions;
    private final EndorsementRepository endorsements;
    private final RefundRecordRepository refunds;
    private final RuleSetService ruleSets;
    private final PolicyTerms policyTerms;
    private final CancellationDateRule dateRule;
    private final RefundPremiumBasis premiumBasis;
    private final ProRataRefundCalculator calculator;
    private final Clock clock;
    private final PolicyStateMachine stateMachine = new PolicyStateMachine();

    public CancellationService(PolicyRepository policies, PolicyVersionRepository versions, PolicyTermRepository terms,
            PolicyStatusTransitionRepository transitions, EndorsementRepository endorsements,
            RefundRecordRepository refunds, RuleSetService ruleSets, PolicyTerms policyTerms,
            CancellationDateRule dateRule, RefundPremiumBasis premiumBasis, ProRataRefundCalculator calculator,
            Clock clock) {
        this.policies = policies;
        this.versions = versions;
        this.terms = terms;
        this.transitions = transitions;
        this.endorsements = endorsements;
        this.refunds = refunds;
        this.ruleSets = ruleSets;
        this.policyTerms = policyTerms;
        this.dateRule = dateRule;
        this.premiumBasis = premiumBasis;
        this.calculator = calculator;
        this.clock = clock;
    }

    /**
     * What cancelling the customer's policy on the date would refund. Writes nothing.
     *
     * @throws ValidationFailedException   (400) for a malformed {@code date}, or {@code CANCELLATION_DATE_OUT_OF_TERM}
     *                                     naming {@code date}
     * @throws ResourceNotFoundException   (404) for an unknown or malformed number, or another customer's policy
     * @throws InvalidPolicyStateException (409) when the policy is not in force
     */
    @Transactional(readOnly = true)
    public RefundPreview preview(RefundPreviewCommand command) {
        LocalDate requested = null;
        if (command.date() != null) {
            requested = parseDate(command.date(), DATE_FIELD);
        }
        return prepare(command.customerId(), command.policyNumber(), requested, DATE_FIELD).preview();
    }

    /**
     * Cancels the customer's policy: one refund record, the move to CANCELLED and its transition, all or nothing.
     *
     * @throws ValidationFailedException   (400) for an unknown key, a malformed date, a missing or unknown reason, or
     *                                     {@code CANCELLATION_DATE_OUT_OF_TERM} naming {@code cancellationDate}
     * @throws ResourceNotFoundException   (404) for an unknown or malformed number, or another customer's policy
     * @throws InvalidPolicyStateException (409) when the policy is not in force
     * @throws ConflictException           (409 {@code POLICY_UPDATE_CONFLICT}) when a concurrent request won
     */
    @Transactional
    public CancellationResult cancel(CancelCommand command) {
        // P-5 step 1: the body shape, before anything is looked up. Every fault is reported, none is echoed.
        List<FieldViolation> problems = new ArrayList<>();
        command.unknownKeys().stream().map(EchoedKeySanitiser::sanitise).distinct()
                .forEach(key -> problems.add(new FieldViolation(key, "is not an accepted field")));
        LocalDate requested = null;
        if (command.cancellationDate() != null) {
            try {
                requested = LocalDate.parse(command.cancellationDate());
            } catch (DateTimeParseException unreadable) {
                problems.add(new FieldViolation(CANCELLATION_DATE_FIELD, NOT_A_DATE));
            }
        }
        CancellationReason reason = CancellationReason.parse(command.reason()).orElse(null);
        if (reason == null) {
            problems.add(new FieldViolation(REASON_FIELD, NOT_A_REASON));
        }
        if (!problems.isEmpty()) {
            throw new ValidationFailedException(problems);
        }

        // P-5 steps 2 to 4: the policy and its owner, its state, and the date window.
        Prepared prepared = prepare(command.customerId(), command.policyNumber(), requested, CANCELLATION_DATE_FIELD);
        PolicyEntity policy = prepared.policy();
        PolicyTermEntity term = prepared.term();
        RefundPreview figures = prepared.preview();
        RefundQuote quote = figures.quote();
        String number = policy.getPolicyNumber();
        Instant now = Instant.now(clock);
        PolicyStatus from = policy.getStatus();

        RefundRecordEntity record;
        try {
            record = refunds.save(RefundRecordEntity.of(number, term.getTermNo(), figures.cancellationDate(),
                    figures.premium(), quote.termDays(), quote.unexpiredDays(), quote.coolingOff(), quote.fee(),
                    quote.amount(), quote.formula(), command.customerId(), now));
            policy.applyTransition(PolicyStatus.CANCELLED, policy.getCurrentVersionNo());
            policies.saveAndFlush(policy);
            transitions.save(PolicyStatusTransitionEntity.of(number, from, PolicyStatus.CANCELLED, reason.name(),
                    command.customerId(), now));
        } catch (ConcurrencyFailureException lostRace) {
            throw new ConflictException(ConflictException.POLICY_UPDATE_CONFLICT);
        } catch (DataIntegrityViolationException failure) {
            if (WriteConflicts.isUniqueViolation(failure)) {
                throw new ConflictException(ConflictException.POLICY_UPDATE_CONFLICT);
            }
            throw failure;
        }

        LOG.info("Policy cancelled policyNumber={} termNo={} coolingOff={}", number, term.getTermNo(),
                quote.coolingOff());
        PolicyVersionEntity current = versions.findByPolicyNumberAndVersionNo(number, policy.getCurrentVersionNo())
                .orElseThrow(() -> new IllegalStateException("Policy has no current version: policyNumber=" + number));
        return new CancellationResult(PolicyDetail.RefundView.of(record),
                PolicyHeader.of(policy, current, term, prepared.endorsementCount()));
    }

    /** Everything a validated request resolves to: the loaded policy, its current paid term and the domain's figures. */
    private record Prepared(PolicyEntity policy, PolicyTermEntity term, int endorsementCount, RefundPreview preview) {
    }

    /** P-5 steps 2 to 4, shared by the preview and the cancel, so the preview shows what the cancel would write. */
    private Prepared prepare(String customerId, String policyNumber, LocalDate requested, String dateField) {
        PolicyEntity policy = loadOwned(customerId, policyNumber);
        String number = policy.getPolicyNumber();

        // The state machine refuses CANCELLED for a LAPSED or CANCELLED policy (AC-10.3).
        stateMachine.transition(policy.getStatus(), PolicyStatus.CANCELLED);

        List<PolicyTermEntity> allTerms = terms.findByPolicyNumberOrderByTermNoAsc(number);
        PolicyTermEntity term = policyTerms.requireCurrentPaid(allTerms, number);
        LocalDate date;
        try {
            date = dateRule.resolve(requested, LocalDate.now(clock), term.getStartDate(), term.getEndDate(), dateField);
        } catch (InvalidCancellationDateException outside) {
            throw new ValidationFailedException(outside.code(),
                    List.of(new FieldViolation(outside.field(), outside.getMessage())));
        }

        // The fee and the cooling-off period are those of the rule version the current paid term was rated with.
        Lifecycle lifecycle = ruleSets.version(policy.getProductCode(), term.getRuleVersion()).lifecycle();
        List<EndorsementEntity> endorsementRows = endorsements.findByPolicyNumberOrderBySequenceNoAsc(number);
        List<DatedAdjustment> adjustments = endorsementRows.stream()
                .map(row -> new DatedAdjustment(row.getEffectiveDate(), row.getAdjustment())).toList();
        BigDecimal basis = premiumBasis.basis(term.getPremium(), term.getStartDate(), term.getEndDate(), adjustments);
        // The cooling-off period runs from the policy's effective date: the first day of term 1 (the lowest term number).
        LocalDate policyEffectiveDate = allTerms.get(0).getStartDate();
        RefundQuote quote = calculator.calculate(basis, term.getStartDate(), term.getEndDate(), date,
                policyEffectiveDate, term.getTermNo() == 1, lifecycle.coolingOffDays(), lifecycle.cancellationFee());
        return new Prepared(policy, term, endorsementRows.size(),
                new RefundPreview(date, term.getTermNo(), term.getStartDate(), term.getEndDate(), basis, quote));
    }

    private static LocalDate parseDate(String text, String field) {
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException unreadable) {
            throw new ValidationFailedException(List.of(new FieldViolation(field, NOT_A_DATE)));
        }
    }

    /** An unknown, malformed or another customer's policy all look the same: 404. */
    private PolicyEntity loadOwned(String customerId, String number) {
        if (number == null || !PolicyQueryService.POLICY_NUMBER.matcher(number).matches()) {
            throw new ResourceNotFoundException("Policy not found");
        }
        PolicyEntity policy = policies.findByPolicyNumber(number)
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found"));
        if (!policy.getCustomerId().equals(customerId)) {
            throw new ResourceNotFoundException("Policy not found");
        }
        return policy;
    }
}
