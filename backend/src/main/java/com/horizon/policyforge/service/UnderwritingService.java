package com.horizon.policyforge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizon.policyforge.domain.PiiMasker;
import com.horizon.policyforge.domain.application.ApplicationStatus;
import com.horizon.policyforge.domain.application.DecisionSource;
import com.horizon.policyforge.domain.application.InvalidApplicationStateException;
import com.horizon.policyforge.domain.application.InvalidDecisionException;
import com.horizon.policyforge.domain.application.ManualDecision;
import com.horizon.policyforge.domain.application.ManualDecisionOutcome;
import com.horizon.policyforge.domain.application.ManualDecisionRules;
import com.horizon.policyforge.domain.rating.RiskInputSchema;
import com.horizon.policyforge.domain.underwriting.UnderwritingOutcome;
import com.horizon.policyforge.repository.ApplicationEntity;
import com.horizon.policyforge.repository.ApplicationRepository;
import com.horizon.policyforge.repository.QuoteEntity;
import com.horizon.policyforge.repository.QuoteRepository;
import com.horizon.policyforge.repository.UnderwritingDecisionEntity;
import com.horizon.policyforge.repository.UnderwritingDecisionRepository;
import com.horizon.policyforge.service.ValidationFailedException.FieldViolation;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The underwriter side of an application (AC-09): the queue of cases to review, a manual decision on a REFERRED
 * application and an admin override of a DECLINED one.
 *
 * <p>Every rule is the domain's ({@link ManualDecisionRules}). This class loads, delegates, appends and audits, and it
 * checks in the C3 order: the body first (400, before any lookup), then the application (404), then its state (409). A
 * decision changes the status, appends the decision row and appends the audit entry in one transaction. The status is
 * flushed first, so a decision that loses the optimistic-lock race (U-9) fails before anything is appended and is
 * reported as the same 409 as a decision on an application that has already moved on.
 *
 * <p>It logs application IDs and actor IDs only (NFR-03): never a name, a KYC value, a risk input or a comment.
 */
@Service
public class UnderwritingService {

    private static final Logger LOG = LoggerFactory.getLogger(UnderwritingService.class);

    static final String DECISION_ACTION = "UNDERWRITING_DECISION";
    static final String OVERRIDE_ACTION = "UNDERWRITING_OVERRIDE";

    /** The shape of an ID this application issues ({@code A-<UUID>}). Anything else is not found, without a lookup. */
    static final Pattern APPLICATION_ID =
            Pattern.compile("A-[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");

    private static final TypeReference<LinkedHashMap<String, Object>> INPUTS = new TypeReference<>() {
    };

    private final ApplicationRepository applications;
    private final QuoteRepository quotes;
    private final UnderwritingDecisionRepository decisions;
    private final RuleSetService ruleSets;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final Clock clock;

    public UnderwritingService(ApplicationRepository applications, QuoteRepository quotes,
            UnderwritingDecisionRepository decisions, RuleSetService ruleSets, AuditService audit,
            ObjectMapper mapper, Clock clock) {
        this.applications = applications;
        this.quotes = quotes;
        this.decisions = decisions;
        this.ruleSets = ruleSets;
        this.audit = audit;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * The cases to review, oldest submission first: the REFERRED applications, and with {@code includeDeclined} the
     * DECLINED ones too (the web layer lets only an ADMIN ask for them, U-11). Each entry is an allow-list (U-7).
     */
    @Transactional(readOnly = true)
    public List<QueueEntry> queue(boolean includeDeclined) {
        List<String> statuses = includeDeclined
                ? List.of(ApplicationStatus.REFERRED.name(), ApplicationStatus.DECLINED.name())
                : List.of(ApplicationStatus.REFERRED.name());
        List<ApplicationEntity> cases = applications.findByStatusInOrderBySubmittedAtAscIdAsc(statuses);
        if (cases.isEmpty()) {
            return List.of();
        }
        Map<String, QuoteEntity> quotesById = quotes
                .findByQuoteIdIn(cases.stream().map(ApplicationEntity::getQuoteId).toList()).stream()
                .collect(Collectors.toMap(QuoteEntity::getQuoteId, Function.identity()));
        Map<String, UnderwritingDecisionEntity> automated = decisions
                .findByApplicationIdInAndSourceOrderByIdAsc(
                        cases.stream().map(ApplicationEntity::getApplicationId).toList(), DecisionSource.RULES.name())
                .stream().collect(Collectors.toMap(UnderwritingDecisionEntity::getApplicationId, Function.identity(),
                        (first, later) -> first));
        Map<String, RiskInputSchema> schemas = new HashMap<>();
        List<QueueEntry> entries = new ArrayList<>();
        for (ApplicationEntity application : cases) {
            entries.add(toEntry(application, quotesById.get(application.getQuoteId()),
                    automated.get(application.getApplicationId()), schemas));
        }
        return entries;
    }

    /**
     * An underwriter's decision on a REFERRED application: APPROVE gives APPROVED and DECLINE gives DECLINED.
     *
     * @throws ValidationFailedException (400) for an unknown key or a body the domain rejects
     * @throws ResourceNotFoundException (404) for an unknown or malformed application ID
     * @throws ConflictException         (409) when the application is not REFERRED or the decision lost a race
     */
    @Transactional
    public DecisionResult decide(DecideCommand command) {
        // Body first (C3): the REFERRED state stands in, so that only the input rules can fail here.
        validateBody(command.unknownKeys(), () -> ManualDecisionRules.decide(ApplicationStatus.REFERRED,
                command.decision(), command.reasonCode(), command.comment()));
        ApplicationEntity application = load(command.applicationId());
        ManualDecisionOutcome outcome = applyRule(() -> ManualDecisionRules.decide(application.getStatus(),
                command.decision(), command.reasonCode(), command.comment()));
        return record(application, outcome, command.actorId(), DecisionSource.MANUAL, command.decision(),
                DECISION_ACTION);
    }

    /**
     * An admin's override of a DECLINED application, which gives APPROVED. The original decision rows are kept.
     *
     * @throws ValidationFailedException (400) for an unknown key or a body the domain rejects
     * @throws ResourceNotFoundException (404) for an unknown or malformed application ID
     * @throws ConflictException         (409) when the application is not DECLINED or the override lost a race
     */
    @Transactional
    public DecisionResult override(OverrideCommand command) {
        validateBody(command.unknownKeys(), () -> ManualDecisionRules.override(ApplicationStatus.DECLINED,
                command.reasonCode(), command.comment()));
        ApplicationEntity application = load(command.applicationId());
        ManualDecisionOutcome outcome = applyRule(
                () -> ManualDecisionRules.override(application.getStatus(), command.reasonCode(), command.comment()));
        return record(application, outcome, command.actorId(), DecisionSource.OVERRIDE, ManualDecision.APPROVE,
                OVERRIDE_ACTION);
    }

    /** Moves the application, then appends the decision row and the audit entry, all in the caller's transaction. */
    private DecisionResult record(ApplicationEntity application, ManualDecisionOutcome outcome, String actorId,
            DecisionSource source, ManualDecision decision, String action) {
        ApplicationStatus previous = application.getStatus();
        application.applyTransition(outcome.newStatus());
        try {
            applications.saveAndFlush(application);
        } catch (ConcurrencyFailureException lostRace) {
            // Another decision changed the application after it was loaded (U-9): the second one is refused.
            throw new ConflictException(ConflictException.INVALID_APPLICATION_STATE);
        }

        String applicationId = application.getApplicationId();
        String reasonCode = outcome.reason().code();
        Instant now = Instant.now(clock);
        decisions.save(UnderwritingDecisionEntity.of(applicationId, source, decision.name(), reasonCode,
                ReasonsJson.write(mapper, List.of(outcome.reason())), ruleVersionOf(applicationId), actorId,
                outcome.comment(), now));
        audit.recordStatusChange(actorId, action, ApplicationService.ENTITY_TYPE, applicationId, reasonCode,
                outcome.comment(), previous.name(), outcome.newStatus().name());

        LOG.info("Underwriting action applicationId={} action={} actorId={} previousStatus={} status={}",
                applicationId, action, actorId, previous, outcome.newStatus());
        return new DecisionResult(applicationId, previous, outcome.newStatus());
    }

    /** The rule version of the application's automated evaluation, which a manual row carries (the column is required). */
    private int ruleVersionOf(String applicationId) {
        return decisions.findByApplicationIdOrderByIdAsc(applicationId).stream()
                .filter(row -> row.getSource() == DecisionSource.RULES)
                .findFirst()
                .map(UnderwritingDecisionEntity::getRuleVersion)
                .orElseThrow(() -> new IllegalStateException("Application has no automated decision: applicationId="
                        + applicationId));
    }

    private ApplicationEntity load(String applicationId) {
        if (applicationId == null || !APPLICATION_ID.matcher(applicationId).matches()) {
            throw new ResourceNotFoundException("Application not found");
        }
        return applications.findByApplicationId(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
    }

    /** Reports every unknown key and every domain input violation together as one 400. */
    private static void validateBody(List<String> unknownKeys, Runnable inputRules) {
        List<FieldViolation> problems = new ArrayList<>();
        unknownKeys.forEach(key -> problems.add(new FieldViolation(key, "is not an accepted field")));
        try {
            inputRules.run();
        } catch (InvalidDecisionException invalid) {
            invalid.violations().forEach(v -> problems.add(new FieldViolation(v.field(), v.message())));
        }
        if (!problems.isEmpty()) {
            throw new ValidationFailedException(problems);
        }
    }

    /** Runs a domain rule against the loaded application and maps its failures to 400 and 409. */
    private static ManualDecisionOutcome applyRule(Supplier<ManualDecisionOutcome> rule) {
        try {
            return rule.get();
        } catch (InvalidDecisionException invalid) {
            throw new ValidationFailedException(invalid.violations().stream()
                    .map(v -> new FieldViolation(v.field(), v.message())).toList());
        } catch (InvalidApplicationStateException state) {
            throw new ConflictException(ConflictException.INVALID_APPLICATION_STATE);
        }
    }

    private QueueEntry toEntry(ApplicationEntity application, QuoteEntity quote, UnderwritingDecisionEntity automated,
            Map<String, RiskInputSchema> schemas) {
        if (quote == null || automated == null) {
            throw new IllegalStateException(
                    "Application is missing its quote or its automated decision: applicationId="
                            + application.getApplicationId());
        }
        RiskInputSchema schema = schemas.computeIfAbsent(quote.getProductCode() + "/" + quote.getRuleVersion(),
                key -> RiskInputSchema
                        .forModel(ruleSets.version(quote.getProductCode(), quote.getRuleVersion()).model()));
        return new QueueEntry(application.getApplicationId(), application.getQuoteId(), application.getProductCode(),
                application.getStatus(), application.getSubmittedAt(), automated.getRuleVersion(),
                UnderwritingOutcome.valueOf(automated.getOutcome()),
                ReasonsJson.read(mapper, automated.getReasonsJson()), application.getKycFullName(),
                PiiMasker.mask(application.getKycAadhaar()), PiiMasker.mask(application.getKycPan()),
                schema.reviewView(readInputs(quote.getRiskInputsJson())));
    }

    private Map<String, Object> readInputs(String json) {
        try {
            // Decimal numbers stay exact: they are read as BigDecimal, never as double.
            return mapper.readerFor(INPUTS).with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored quote data cannot be read", e);
        }
    }
}
