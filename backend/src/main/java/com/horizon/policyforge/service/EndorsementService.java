package com.horizon.policyforge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizon.policyforge.domain.Money;
import com.horizon.policyforge.domain.endorsement.EndorsementCommand;
import com.horizon.policyforge.domain.endorsement.EndorsementPremiumAdjuster;
import com.horizon.policyforge.domain.endorsement.EndorsementRequest;
import com.horizon.policyforge.domain.endorsement.EndorsementRequestParser;
import com.horizon.policyforge.domain.endorsement.EndorsementRequestParser.Parsed;
import com.horizon.policyforge.domain.endorsement.EndorsementRequestParser.ShapeViolation;
import com.horizon.policyforge.domain.endorsement.EndorsementValidation;
import com.horizon.policyforge.domain.endorsement.EndorsementValidator;
import com.horizon.policyforge.domain.endorsement.Nominee;
import com.horizon.policyforge.domain.endorsement.PolicySnapshot;
import com.horizon.policyforge.domain.endorsement.PremiumAdjustment;
import com.horizon.policyforge.domain.policy.InvalidPolicyStateException;
import com.horizon.policyforge.domain.policy.PolicyStatus;
import com.horizon.policyforge.domain.rating.InvalidRiskInputException;
import com.horizon.policyforge.domain.rating.PremiumBreakdown;
import com.horizon.policyforge.domain.rating.PremiumCalculator;
import com.horizon.policyforge.domain.ruleset.RuleSetVersion;
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
import com.horizon.policyforge.service.ValidationFailedException.FieldViolation;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The endorsement use cases (AC-06): apply a mid-term change to an in-force policy, or preview it. The checks run in the
 * P-5 order: the request shape (400, no code), then the policy lookup and its owner (404), then the product's
 * sum-insured keys (400, no code), then the policy's state (409 {@code InvalidPolicyStateException}), then the business
 * rules E2 to E10 (400, all collected, with the first code). Every rule is the domain's: the request parser, the
 * {@link EndorsementValidator}, the {@link PremiumCalculator} with the policy's own rule version, and the
 * {@link EndorsementPremiumAdjuster}. This class loads, delegates, appends and logs.
 *
 * <p>{@link #endorse} writes in one transaction, in this order: the endorsement (sequence n + 1, before version =
 * current, after version = current + 1), the new policy version, then the policy's current version number and status
 * through the aggregate's state machine and the {@code -> ENDORSED} transition row. If any step fails nothing is kept
 * (AC-06.3). Two concurrent endorsements of one policy compute the same sequence and version number, so the unique
 * keys and the policy's optimistic lock let one win and make the other fail, and that one answers 409
 * {@code POLICY_UPDATE_CONFLICT} with nothing written (AC-06.6).
 *
 * <p>The request carries an address or a nominee: this class logs the policy number, the sequence number, the type and
 * the version numbers only (NFR-03).
 */
@Service
public class EndorsementService {

    private static final Logger LOG = LoggerFactory.getLogger(EndorsementService.class);

    private static final TypeReference<LinkedHashMap<String, Object>> INPUTS = new TypeReference<>() {
    };
    private static final TypeReference<List<Nominee>> NOMINEES = new TypeReference<>() {
    };
    /** The SQL state of a unique-key violation, in H2 and in PostgreSQL. */
    private static final String UNIQUE_VIOLATION = "23505";

    private final PolicyRepository policies;
    private final PolicyVersionRepository versions;
    private final PolicyTermRepository terms;
    private final PolicyStatusTransitionRepository transitions;
    private final EndorsementRepository endorsements;
    private final RuleSetService ruleSets;
    private final EndorsementRequestParser parser;
    private final EndorsementValidator validator;
    private final EndorsementPremiumAdjuster adjuster;
    private final PremiumCalculator calculator;
    private final EndorsementViews views;
    private final ObjectMapper mapper;
    private final Clock clock;

    public EndorsementService(PolicyRepository policies, PolicyVersionRepository versions,
            PolicyTermRepository terms, PolicyStatusTransitionRepository transitions,
            EndorsementRepository endorsements, RuleSetService ruleSets, EndorsementRequestParser parser,
            EndorsementValidator validator, EndorsementPremiumAdjuster adjuster, PremiumCalculator calculator,
            EndorsementViews views, ObjectMapper mapper, Clock clock) {
        this.policies = policies;
        this.versions = versions;
        this.terms = terms;
        this.transitions = transitions;
        this.endorsements = endorsements;
        this.ruleSets = ruleSets;
        this.parser = parser;
        this.validator = validator;
        this.adjuster = adjuster;
        this.calculator = calculator;
        this.views = views;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Applies the endorsement to the customer's policy.
     *
     * @throws ValidationFailedException   (400) for a shape problem, another product's sum-insured key, or a business
     *                                     rule E2 to E10 (with the first code)
     * @throws ResourceNotFoundException   (404) for an unknown or malformed number, or another customer's policy
     * @throws InvalidPolicyStateException (409) when the policy is not in force
     * @throws ConflictException           (409 {@code POLICY_UPDATE_CONFLICT}) when a concurrent endorsement won
     */
    @Transactional
    public EndorsementResult endorse(EndorseCommand command) {
        Prepared prepared = prepare(command);
        PolicyEntity policy = prepared.policy();
        PolicyVersionEntity current = prepared.current();
        EndorsementCommand change = prepared.change();
        String policyNumber = policy.getPolicyNumber();
        Instant now = Instant.now(clock);
        int sequenceNo = prepared.lastSequenceNo() + 1;
        int beforeVersionNo = policy.getCurrentVersionNo();
        int afterVersionNo = beforeVersionNo + 1;
        PolicyStatus from = policy.getStatus();

        EndorsementEntity endorsement;
        PolicyVersionEntity version;
        try {
            endorsement = endorsements.save(EndorsementEntity.of(policyNumber, sequenceNo, change.type(),
                    views.payloadJson(change), beforeVersionNo, afterVersionNo, prepared.newAnnualPremium(),
                    prepared.adjustment().adjustment(), change.effectiveDate(), command.customerId(), now));
            version = versions.save(PolicyVersionEntity.of(policyNumber, afterVersionNo, prepared.newSumInsured(),
                    prepared.newAnnualPremium(), prepared.newRiskInputsJson(),
                    change.address() != null ? change.address() : current.getAddress(),
                    write(prepared.nominees()), current.getRuleVersion(), change.effectiveDate(),
                    current.getExpiryDate(), now));
            policy.applyTransition(PolicyStatus.ENDORSED, afterVersionNo);
            policies.saveAndFlush(policy);
            transitions.save(PolicyStatusTransitionEntity.of(policyNumber, from, PolicyStatus.ENDORSED,
                    change.type().name(), command.customerId(), now));
        } catch (ConcurrencyFailureException lostRace) {
            throw new ConflictException(ConflictException.POLICY_UPDATE_CONFLICT);
        } catch (DataIntegrityViolationException failure) {
            if (isUniqueViolation(failure)) {
                throw new ConflictException(ConflictException.POLICY_UPDATE_CONFLICT);
            }
            throw failure;
        }

        LOG.info("Policy endorsed policyNumber={} sequenceNo={} type={} afterVersionNo={}", policyNumber, sequenceNo,
                change.type(), afterVersionNo);
        // The sequence numbers run 1..n without gaps, so the new endorsement's sequence is the endorsement count.
        PolicyHeader header = PolicyHeader.of(policy, version, prepared.term(), sequenceNo);
        return new EndorsementResult(views.view(endorsement), header);
    }

    /**
     * What {@link #endorse} would do for the same request, with the same checks in the same order. Writes nothing.
     *
     * @throws ValidationFailedException   (400)
     * @throws ResourceNotFoundException   (404)
     * @throws InvalidPolicyStateException (409)
     */
    @Transactional(readOnly = true)
    public EndorsementPreview preview(EndorseCommand command) {
        Prepared prepared = prepare(command);
        PremiumAdjustment adjustment = prepared.adjustment();
        return new EndorsementPreview(prepared.change().type(), prepared.change().effectiveDate(),
                prepared.current().getAnnualPremium(), prepared.newAnnualPremium(), adjustment.adjustment(),
                adjustment.unexpiredDays(), adjustment.termDays());
    }

    /** Everything a validated request resolves to: the loaded policy, the domain's decision and the re-rated money. */
    private record Prepared(PolicyEntity policy, PolicyVersionEntity current, PolicyTermEntity term,
            int lastSequenceNo, EndorsementCommand change, List<Nominee> nominees, BigDecimal newSumInsured,
            BigDecimal newAnnualPremium, String newRiskInputsJson, PremiumAdjustment adjustment) {
    }

    private Prepared prepare(EndorseCommand command) {
        // P-5 step 1: the request shape, before anything is looked up.
        Parsed parsed = parser.parse(command.body());
        if (!parsed.isValid()) {
            throw shapeFailure(parsed.violations());
        }
        EndorsementRequest request = parsed.request();

        // P-5 step 2: the policy and its owner. Another customer's policy looks the same as an unknown one.
        PolicyEntity policy = loadOwned(command);
        String productCode = policy.getProductCode();
        List<ShapeViolation> productKeys = parser.checkProduct(productCode, request);
        if (!productKeys.isEmpty()) {
            throw shapeFailure(productKeys);
        }

        PolicyVersionEntity current = versions
                .findByPolicyNumberAndVersionNo(policy.getPolicyNumber(), policy.getCurrentVersionNo())
                .orElseThrow(() -> new IllegalStateException("Policy has no current version: policyNumber="
                        + policy.getPolicyNumber()));
        PolicyTermEntity term = terms.findByPolicyNumberOrderByTermNoAsc(policy.getPolicyNumber()).stream()
                .max(Comparator.comparingInt(PolicyTermEntity::getTermNo))
                .orElseThrow(() -> new IllegalStateException("Policy has no term: policyNumber="
                        + policy.getPolicyNumber()));
        EndorsementEntity last = endorsements.findFirstByPolicyNumberOrderBySequenceNoDesc(policy.getPolicyNumber())
                .orElse(null);
        RuleSetVersion rules = ruleSets.version(productCode, current.getRuleVersion());
        Map<String, Object> currentInputs = read(current.getRiskInputsJson(), INPUTS);
        List<Nominee> nominees = read(current.getNomineesJson(), NOMINEES);

        // P-5 steps 3 and 4: the state (E1, raised by the domain) and then every business rule (E2 to E10).
        PolicySnapshot snapshot = new PolicySnapshot(productCode, policy.getStatus(), term.getStartDate(),
                term.getEndDate(), last == null ? null : last.getEffectiveDate(), nominees,
                sumInsuredComponents(productCode, currentInputs), rules.sumInsured());
        EndorsementValidation validation = validator.validate(snapshot, request, LocalDate.now(clock));
        if (!validation.isValid()) {
            throw new ValidationFailedException(validation.firstCode(), validation.violations().stream()
                    .map(violation -> new FieldViolation(violation.field(), violation.message())).toList());
        }
        EndorsementCommand change = validation.command();

        BigDecimal newSumInsured = current.getSumInsured();
        BigDecimal newAnnualPremium = current.getAnnualPremium();
        String newRiskInputsJson = current.getRiskInputsJson();
        if (change.type().reRates()) {
            // The policy's own rule version, through the same calculator as a quote: no rating logic is repeated here.
            Map<String, Object> modified = adjuster.modifiedRiskInputs(productCode, currentInputs,
                    change.newSumInsured());
            PremiumBreakdown priced;
            try {
                priced = calculator.calculate(rules, modified);
            } catch (InvalidRiskInputException invalid) {
                throw new ValidationFailedException(invalid.violations().stream()
                        .map(violation -> new FieldViolation(violation.field(), violation.message())).toList());
            }
            newSumInsured = priced.sumInsured();
            newAnnualPremium = priced.annualPremium();
            newRiskInputsJson = write(modified);
        }
        PremiumAdjustment adjustment = adjuster.adjust(current.getAnnualPremium(), newAnnualPremium,
                term.getStartDate(), term.getEndDate(), change.effectiveDate());

        List<Nominee> newNominees = nominees;
        if (change.nominee() != null) {
            newNominees = new ArrayList<>(nominees);
            newNominees.add(change.nominee());
        }
        return new Prepared(policy, current, term, last == null ? 0 : last.getSequenceNo(), change, newNominees,
                newSumInsured, newAnnualPremium, newRiskInputsJson, adjustment);
    }

    /** P-5 step 2: an unknown, malformed or another customer's policy all look the same: 404. */
    private PolicyEntity loadOwned(EndorseCommand command) {
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

    private static ValidationFailedException shapeFailure(List<ShapeViolation> violations) {
        return new ValidationFailedException(violations.stream()
                .map(violation -> new FieldViolation(violation.field(), violation.message())).toList());
    }

    /** The current sum insured by risk-input name, from the stored inputs (the keys the validator and adjuster use). */
    private static Map<String, BigDecimal> sumInsuredComponents(String productCode, Map<String, Object> inputs) {
        List<String> keys = switch (productCode) {
            case "TERM_LIFE" -> List.of("sumInsured");
            case "MOTOR" -> List.of("vehicleValue");
            case "HOUSEHOLD" -> List.of("buildingSumInsured", "contentsSumInsured");
            default -> throw new IllegalStateException("Unknown product on a policy");
        };
        Map<String, BigDecimal> components = new LinkedHashMap<>();
        for (String key : keys) {
            Object value = inputs.get(key);
            if (value == null) {
                throw new IllegalStateException("The stored risk inputs have no " + key);
            }
            components.put(key, Money.finalAmount(new BigDecimal(String.valueOf(value))));
        }
        return components;
    }

    /** Whether the failure is a unique-key violation, as opposed to any other broken constraint. */
    private static boolean isUniqueViolation(DataIntegrityViolationException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise policy data", e);
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored policy data cannot be read", e);
        }
    }
}
