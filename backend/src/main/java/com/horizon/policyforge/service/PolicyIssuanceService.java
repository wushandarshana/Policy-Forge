package com.horizon.policyforge.service;

import com.horizon.policyforge.domain.application.ApplicationStatus;
import com.horizon.policyforge.domain.application.InvalidApplicationStateException;
import com.horizon.policyforge.domain.endorsement.Address;
import com.horizon.policyforge.domain.policy.InvalidEffectiveDateException;
import com.horizon.policyforge.domain.policy.IssuanceDates;
import com.horizon.policyforge.domain.policy.PolicyStatus;
import com.horizon.policyforge.domain.ruleset.RuleSetVersion;
import com.horizon.policyforge.repository.ApplicationEntity;
import com.horizon.policyforge.repository.ApplicationRepository;
import com.horizon.policyforge.repository.PolicyEntity;
import com.horizon.policyforge.repository.PolicyRepository;
import com.horizon.policyforge.repository.PolicyStatusTransitionEntity;
import com.horizon.policyforge.repository.PolicyStatusTransitionRepository;
import com.horizon.policyforge.repository.PolicyTermEntity;
import com.horizon.policyforge.repository.PolicyTermRepository;
import com.horizon.policyforge.repository.PolicyVersionEntity;
import com.horizon.policyforge.repository.PolicyVersionRepository;
import com.horizon.policyforge.repository.QuoteEntity;
import com.horizon.policyforge.repository.QuoteRepository;
import com.horizon.policyforge.service.ValidationFailedException.FieldViolation;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The issuance use case (AC-05): turns an APPROVED application into an ACTIVE policy. The checks run in the P-5 order:
 * the request shape and the date window (400), then the application lookup and its owner (404), then the application's
 * state (409). On success, in one transaction: the application moves APPROVED to ISSUED; the next policy number is
 * allocated; the policy, its version 1, its term 1 (paid) and the {@code null -> ACTIVE} transition are appended; and an
 * {@code APPLICATION_ISSUED} audit entry is written (P-10). Every business decision is made by the domain classes. This
 * class loads, delegates, appends and audits.
 *
 * <p>Sum insured, premium, risk inputs and rule version are copied from the application's quote, so the policy keeps
 * the rule version it was priced with. It logs the policy number and the application ID only (NFR-03): the KYC details,
 * the address and the risk inputs are never passed to a logger.
 */
@Service
public class PolicyIssuanceService {

    private static final Logger LOG = LoggerFactory.getLogger(PolicyIssuanceService.class);

    static final String ISSUED_ACTION = "APPLICATION_ISSUED";
    static final String ISSUANCE_REASON = "ISSUANCE";
    /** Version 1 has no nominees (policy-issuance spec). */
    private static final String NO_NOMINEES_JSON = "[]";
    private static final int FIRST_VERSION = 1;
    private static final int FIRST_TERM = 1;
    /**
     * An unknown key is named in the 400, so it is cut to this many characters first: the same bound the endorsement
     * parser uses, so a key of any length is never reflected in full.
     */
    private static final int MAX_ECHOED_KEY = 100;

    private final ApplicationRepository applications;
    private final QuoteRepository quotes;
    private final PolicyRepository policies;
    private final PolicyVersionRepository versions;
    private final PolicyTermRepository terms;
    private final PolicyStatusTransitionRepository transitions;
    private final RuleSetService ruleSets;
    private final PolicyNumberGenerator numbers;
    private final AuditService audit;
    private final Clock clock;

    public PolicyIssuanceService(ApplicationRepository applications, QuoteRepository quotes,
            PolicyRepository policies, PolicyVersionRepository versions, PolicyTermRepository terms,
            PolicyStatusTransitionRepository transitions, RuleSetService ruleSets, PolicyNumberGenerator numbers,
            AuditService audit, Clock clock) {
        this.applications = applications;
        this.quotes = quotes;
        this.policies = policies;
        this.versions = versions;
        this.terms = terms;
        this.transitions = transitions;
        this.ruleSets = ruleSets;
        this.numbers = numbers;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Issues the customer's APPROVED application.
     *
     * @throws ValidationFailedException (400) for an unknown key, a malformed date or one outside today to today + 60
     *                                   (code {@code EFFECTIVE_DATE_OUT_OF_RANGE})
     * @throws ResourceNotFoundException (404) for an unknown or malformed application ID or another customer's
     * @throws ConflictException         (409) when the application is not APPROVED (including already issued), or a
     *                                   concurrent issue of the same application won
     */
    @Transactional
    public PolicyHeader issue(IssuePolicyCommand command) {
        LocalDate today = LocalDate.now(clock);
        LocalDate effectiveDate = resolveEffectiveDate(command, today);
        ApplicationEntity application = loadOwned(command);

        try {
            application.applyTransition(ApplicationStatus.ISSUED);
        } catch (InvalidApplicationStateException notApproved) {
            throw new ConflictException(ConflictException.INVALID_APPLICATION_STATE);
        }
        try {
            applications.saveAndFlush(application);
        } catch (ConcurrencyFailureException lostRace) {
            // Another issue of this application changed it after it was loaded: the second one is refused (U-9).
            throw new ConflictException(ConflictException.INVALID_APPLICATION_STATE);
        }

        QuoteEntity quote = quotes.findByQuoteId(application.getQuoteId()).orElseThrow(() -> new IllegalStateException(
                "Application has no quote: applicationId=" + application.getApplicationId()));
        RuleSetVersion rules = ruleSets.version(quote.getProductCode(), quote.getRuleVersion());
        LocalDate expiryDate = IssuanceDates.expiryDate(effectiveDate, rules.lifecycle().termMonths());
        String policyNumber = numbers.next(quote.getProductCode(), today.getYear());
        Instant now = Instant.now(clock);
        String customerId = command.customerId();

        PolicyEntity policy = PolicyEntity.issue(policyNumber, application.getApplicationId(),
                quote.getProductCode(), customerId, now);
        try {
            policies.saveAndFlush(policy);
        } catch (DataIntegrityViolationException alreadyIssued) {
            // The unique application_id is the backstop for a second policy on one application (C7).
            throw new ConflictException(ConflictException.INVALID_APPLICATION_STATE);
        }
        PolicyVersionEntity version = versions.save(PolicyVersionEntity.of(policyNumber, FIRST_VERSION,
                quote.getSumInsured(), quote.getAnnualPremium(), quote.getRiskInputsJson(), addressOf(application),
                NO_NOMINEES_JSON, quote.getRuleVersion(), effectiveDate, expiryDate, now));
        PolicyTermEntity term = terms.save(PolicyTermEntity.of(policyNumber, FIRST_TERM, effectiveDate, expiryDate,
                quote.getAnnualPremium(), true));
        transitions.save(PolicyStatusTransitionEntity.of(policyNumber, null, PolicyStatus.ACTIVE, ISSUANCE_REASON,
                customerId, now));
        audit.recordStatusChange(customerId, ISSUED_ACTION, ApplicationService.ENTITY_TYPE,
                application.getApplicationId(), null, null, ApplicationStatus.APPROVED.name(),
                ApplicationStatus.ISSUED.name());

        LOG.info("Policy issued policyNumber={} applicationId={} productCode={} ruleVersion={}", policyNumber,
                application.getApplicationId(), quote.getProductCode(), quote.getRuleVersion());
        return PolicyHeader.of(policy, version, term, 0);
    }

    /**
     * P-5 step 1: every shape problem (unknown keys, an unreadable date) is reported together; only a body that is
     * well formed gets the window check, which carries the business code.
     */
    private static LocalDate resolveEffectiveDate(IssuePolicyCommand command, LocalDate today) {
        List<FieldViolation> problems = new ArrayList<>();
        command.unknownKeys().stream().map(PolicyIssuanceService::boundedKey).distinct()
                .forEach(key -> problems.add(new FieldViolation(key, "is not an accepted field")));
        LocalDate requested = null;
        if (command.effectiveDate() != null) {
            try {
                requested = LocalDate.parse(command.effectiveDate());
            } catch (DateTimeParseException unreadable) {
                problems.add(new FieldViolation(InvalidEffectiveDateException.FIELD,
                        "must be a date in the format YYYY-MM-DD"));
            }
        }
        if (!problems.isEmpty()) {
            throw new ValidationFailedException(problems);
        }
        try {
            return IssuanceDates.resolveEffectiveDate(requested, today);
        } catch (InvalidEffectiveDateException outOfRange) {
            throw new ValidationFailedException(outOfRange.code(), List.of(new FieldViolation(outOfRange.field(),
                    "must be between today and 60 days from today")));
        }
    }

    private static String boundedKey(String key) {
        return key.length() > MAX_ECHOED_KEY ? key.substring(0, MAX_ECHOED_KEY) : key;
    }

    /** P-5 step 2: an unknown, malformed or another customer's application all look the same: 404. */
    private ApplicationEntity loadOwned(IssuePolicyCommand command) {
        String applicationId = command.applicationId();
        if (applicationId == null || !UnderwritingService.APPLICATION_ID.matcher(applicationId).matches()) {
            throw new ResourceNotFoundException("Application not found");
        }
        ApplicationEntity application = applications.findByApplicationId(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
        if (!application.getCustomerId().equals(command.customerId())) {
            throw new ResourceNotFoundException("Application not found");
        }
        return application;
    }

    private static Address addressOf(ApplicationEntity application) {
        return new Address(application.getKycAddressLine1(), application.getKycAddressCity(),
                application.getKycAddressPostcode());
    }
}
