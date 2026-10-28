package com.horizon.policyforge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizon.policyforge.domain.application.ApplicationLifecycle;
import com.horizon.policyforge.domain.application.ApplicationStatus;
import com.horizon.policyforge.domain.application.DecisionSource;
import com.horizon.policyforge.domain.application.KycValidator;
import com.horizon.policyforge.domain.application.KycViolation;
import com.horizon.policyforge.domain.rating.RiskInputValidator;
import com.horizon.policyforge.domain.ruleset.RuleSetVersion;
import com.horizon.policyforge.domain.underwriting.ReasonCode;
import com.horizon.policyforge.domain.underwriting.UnderwritingOutcome;
import com.horizon.policyforge.domain.underwriting.UnderwritingResult;
import com.horizon.policyforge.domain.underwriting.UnderwritingRuleEvaluator;
import com.horizon.policyforge.repository.ApplicationEntity;
import com.horizon.policyforge.repository.ApplicationRepository;
import com.horizon.policyforge.repository.QuoteEntity;
import com.horizon.policyforge.repository.QuoteRepository;
import com.horizon.policyforge.repository.UnderwritingDecisionEntity;
import com.horizon.policyforge.repository.UnderwritingDecisionRepository;
import com.horizon.policyforge.service.ValidationFailedException.FieldViolation;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The application use cases (AC-03, AC-04): submit an application for a quote and read a customer's own applications.
 *
 * <p>A submission runs the U-2 checks in order and stores nothing on any failure. On success, in one transaction: the
 * application is stored as SUBMITTED and moved to UNDERWRITING; the rule version active now (U-5) is evaluated; the
 * outcome becomes the status; a {@code RULES} decision row is appended (U-9); and the two U-12 audit entries are
 * written. Every business decision is made by the domain classes. This class loads, delegates, appends and audits.
 *
 * <p>It logs application and quote IDs only (NFR-03): the KYC details, the health inputs and the risk inputs are never
 * passed to a logger.
 */
@Service
public class ApplicationService {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationService.class);

    static final String SYSTEM_ACTOR = "system";
    static final String ENTITY_TYPE = "APPLICATION";

    private static final TypeReference<LinkedHashMap<String, Object>> INPUTS = new TypeReference<>() {
    };
    private static final TypeReference<List<ReasonCode>> REASONS = new TypeReference<>() {
    };

    private final QuoteRepository quotes;
    private final ApplicationRepository applications;
    private final UnderwritingDecisionRepository decisions;
    private final RuleSetService ruleSets;
    private final KycValidator kycValidator;
    private final RiskInputValidator riskInputValidator;
    private final UnderwritingRuleEvaluator evaluator;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ApplicationService(QuoteRepository quotes, ApplicationRepository applications,
            UnderwritingDecisionRepository decisions, RuleSetService ruleSets, KycValidator kycValidator,
            RiskInputValidator riskInputValidator, UnderwritingRuleEvaluator evaluator, AuditService audit,
            ObjectMapper mapper, Clock clock) {
        this.quotes = quotes;
        this.applications = applications;
        this.decisions = decisions;
        this.ruleSets = ruleSets;
        this.kycValidator = kycValidator;
        this.riskInputValidator = riskInputValidator;
        this.evaluator = evaluator;
        this.audit = audit;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Submits and underwrites an application.
     *
     * @throws ValidationFailedException (400) for a shape or KYC format error, an unknown, expired or age-mismatched
     *                                   quote, or risk inputs that no longer pass the active rule set
     * @throws ConflictException         (409) when the quote already has an application
     */
    @Transactional
    public ApplicationResult submit(SubmitApplicationCommand command) {
        LocalDate today = LocalDate.now(clock);
        checkShapeAndKyc(command, today);

        QuoteEntity quote = quotes.findByQuoteId(command.quoteId())
                .orElseThrow(() -> named("QUOTE_NOT_FOUND", "quoteId", "was not found"));
        if (today.isAfter(quote.getValidUntil())) {
            throw named("QUOTE_EXPIRED", "quoteId", "has expired");
        }
        if (applications.existsByQuoteId(quote.getQuoteId())) {
            throw new ConflictException(ConflictException.QUOTE_ALREADY_APPLIED);
        }

        Map<String, Object> riskInputs = readInputs(quote.getRiskInputsJson());
        kycValidator.checkAge(quote.getProductCode(), riskInputs, command.kyc(), today).ifPresent(violation -> {
            throw new ValidationFailedException(KycValidator.AGE_MISMATCH_CODE,
                    List.of(new FieldViolation(violation.field(), violation.message())));
        });

        RuleSetVersion rules = ruleSets.activeVersion(quote.getProductCode());
        List<FieldViolation> riskProblems = riskInputValidator.validate(rules, riskInputs).stream()
                .map(violation -> new FieldViolation(violation.field(), violation.message()))
                .toList();
        if (!riskProblems.isEmpty()) {
            throw new ValidationFailedException(riskProblems);
        }

        String applicationId = "A-" + UUID.randomUUID();
        Instant now = Instant.now(clock);
        ApplicationEntity application = ApplicationEntity.submit(applicationId, quote.getQuoteId(),
                command.customerId(), quote.getProductCode(), command.kyc(), now);
        try {
            applications.saveAndFlush(application);
        } catch (DataIntegrityViolationException raced) {
            // Two submissions for one quote at the same moment: the unique quote_id lets only one of them in (U-3).
            throw new ConflictException(ConflictException.QUOTE_ALREADY_APPLIED);
        }

        ApplicationStatus submitted = application.getStatus();
        application.applyTransition(ApplicationLifecycle.startUnderwriting(submitted));
        audit.recordStatusChange(command.customerId(), "APPLICATION_SUBMITTED", ENTITY_TYPE, applicationId, null, null,
                submitted.name(), application.getStatus().name());

        Map<String, Object> facts = UnderwritingRuleEvaluator.withDerived(riskInputs, quote.getSumInsured(), null);
        UnderwritingResult result = evaluator.evaluate(rules, facts);
        ApplicationStatus underwriting = application.getStatus();
        application.applyTransition(ApplicationLifecycle.applyOutcome(underwriting, result.outcome()));
        decisions.save(UnderwritingDecisionEntity.of(applicationId, DecisionSource.RULES, result.outcome().name(),
                String.join(",", result.reasonCodes()), write(result.reasons()), result.ruleVersion(), SYSTEM_ACTOR,
                null, now));
        audit.recordStatusChange(SYSTEM_ACTOR, "UNDERWRITING_AUTO_DECISION", ENTITY_TYPE, applicationId, null, null,
                underwriting.name(), application.getStatus().name());

        LOG.info("Application submitted applicationId={} quoteId={} productCode={} status={} ruleVersion={}",
                applicationId, quote.getQuoteId(), quote.getProductCode(), application.getStatus(),
                result.ruleVersion());
        return new ApplicationResult(applicationId, quote.getQuoteId(), quote.getProductCode(),
                application.getStatus(), result.outcome(), result.reasons(), result.ruleVersion(), now);
    }

    /** The customer's own applications, newest first (U-13). Never another customer's. */
    @Transactional(readOnly = true)
    public List<ApplicationResult> mine(String customerId) {
        List<ApplicationEntity> own = applications.findByCustomerIdOrderBySubmittedAtDescIdDesc(customerId);
        if (own.isEmpty()) {
            return List.of();
        }
        List<String> ids = own.stream().map(ApplicationEntity::getApplicationId).toList();
        Map<String, UnderwritingDecisionEntity> rulesDecisions = decisions
                .findByApplicationIdInAndSourceOrderByIdAsc(ids, DecisionSource.RULES.name()).stream()
                .collect(Collectors.toMap(UnderwritingDecisionEntity::getApplicationId, Function.identity(),
                        (first, later) -> first));
        return own.stream().map(application -> toResult(application, rulesDecisions)).toList();
    }

    /** Whether the customer already has an application submitted with this Aadhaar. The demo seed uses it (U-14). */
    @Transactional(readOnly = true)
    public boolean hasApplicationWithAadhaar(String customerId, String aadhaar) {
        return applications.existsByCustomerIdAndKycAadhaar(customerId, aadhaar);
    }

    /** U-2 step 1: the request shape and every KYC format error, all reported together. */
    private void checkShapeAndKyc(SubmitApplicationCommand command, LocalDate today) {
        List<FieldViolation> problems = new ArrayList<>();
        command.unknownKeys().forEach(path -> problems.add(new FieldViolation(path, "is not an accepted field")));
        if (command.quoteId() == null || command.quoteId().isBlank()) {
            problems.add(new FieldViolation("quoteId", "is required"));
        }
        for (KycViolation violation : kycValidator.validate(command.kyc(), today)) {
            problems.add(new FieldViolation(violation.field(), violation.message()));
        }
        if (!problems.isEmpty()) {
            throw new ValidationFailedException(problems);
        }
    }

    private static ValidationFailedException named(String code, String field, String message) {
        return new ValidationFailedException(code, List.of(new FieldViolation(field, message)));
    }

    private ApplicationResult toResult(ApplicationEntity application, Map<String, UnderwritingDecisionEntity> rules) {
        UnderwritingDecisionEntity decision = rules.get(application.getApplicationId());
        if (decision == null) {
            throw new IllegalStateException("Application has no automated decision: applicationId="
                    + application.getApplicationId());
        }
        return new ApplicationResult(application.getApplicationId(), application.getQuoteId(),
                application.getProductCode(), application.getStatus(),
                UnderwritingOutcome.valueOf(decision.getOutcome()), readReasons(decision.getReasonsJson()),
                decision.getRuleVersion(), application.getSubmittedAt());
    }

    private Map<String, Object> readInputs(String json) {
        try {
            return mapper.readValue(json, INPUTS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored quote data cannot be read", e);
        }
    }

    private List<ReasonCode> readReasons(String json) {
        try {
            return mapper.readValue(json, REASONS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored decision reasons cannot be read", e);
        }
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise decision reasons", e);
        }
    }
}
