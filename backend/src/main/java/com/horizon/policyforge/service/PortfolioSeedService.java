package com.horizon.policyforge.service;

import com.horizon.policyforge.domain.application.ApplicationStatus;
import com.horizon.policyforge.domain.application.KycAddress;
import com.horizon.policyforge.domain.application.KycInput;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The synthetic sample portfolio (app_spec section 9, plan C10, AC-11.1): six policies for {@code customer1} and
 * {@code customer2}, two per product, so the admin dashboard and the demo are never empty. With T as today on the
 * injected clock:
 *
 * <pre>
 * S1 customer1 TERM_LIFE   term ends T+20     in force, renewal offer pending (near renewal)
 * S2 customer1 MOTOR       term starts T-30   in force
 * S3 customer1 HOUSEHOLD   term starts T-30   in force
 * S4 customer2 TERM_LIFE   term starts T-30   in force
 * S5 customer2 MOTOR       term ended T-5     in force, offer unpaid, in grace
 * S6 customer2 HOUSEHOLD   term starts T-30   in force
 * </pre>
 *
 * <p>Each policy goes through the real services: an owned quote, a submitted application that the rules approve, and
 * issuance, backdated to the effective date by {@link PolicyIssuanceService#issueBackdated}, which has package
 * visibility so that only this layer can reach it. The three steps of one policy are one transaction. When anything was
 * created, one renewal cycle for T then offers the renewal of S1 and S5. The seed is idempotent: a policy's application
 * carrying its synthetic Aadhaar marker means it already exists, so a restart adds nothing and runs no cycle. The data
 * is synthetic, and only IDs are logged (NFR-03).
 */
@Service
public class PortfolioSeedService {

    private static final Logger LOG = LoggerFactory.getLogger(PortfolioSeedService.class);

    private static final String LINE1 = "10 Seed Residency Road";
    private static final String CITY = "Pune";
    private static final String POSTCODE = "411001";
    private static final int TERM_LIFE_AGE = 35;
    private static final int MOTOR_DRIVER_AGE = 30;
    private static final int HOUSEHOLD_HOLDER_AGE = 40;

    /** Which day a seed policy's term is pinned to, relative to T. */
    private enum Anchor {
        /** The first term starts 30 days before T. */
        STARTED_30_DAYS_AGO,
        /** The first term ends 20 days after T. */
        ENDS_IN_20_DAYS,
        /** The first term ended 5 days before T. */
        ENDED_5_DAYS_AGO
    }

    private record SeedPolicy(String name, String customerId, String productCode, int holderAge, String aadhaar,
            String pan, Anchor anchor) {

        String fullName() {
            return "Seed Holder " + name;
        }
    }

    private static final List<SeedPolicy> POLICIES = List.of(
            new SeedPolicy("S1", "customer1", "TERM_LIFE", TERM_LIFE_AGE, "XXXX-XXXX-0101", "ABCDE0101F",
                    Anchor.ENDS_IN_20_DAYS),
            new SeedPolicy("S2", "customer1", "MOTOR", MOTOR_DRIVER_AGE, "XXXX-XXXX-0102", "ABCDE0102F",
                    Anchor.STARTED_30_DAYS_AGO),
            new SeedPolicy("S3", "customer1", "HOUSEHOLD", HOUSEHOLD_HOLDER_AGE, "XXXX-XXXX-0103", "ABCDE0103F",
                    Anchor.STARTED_30_DAYS_AGO),
            new SeedPolicy("S4", "customer2", "TERM_LIFE", TERM_LIFE_AGE, "XXXX-XXXX-0104", "ABCDE0104F",
                    Anchor.STARTED_30_DAYS_AGO),
            new SeedPolicy("S5", "customer2", "MOTOR", MOTOR_DRIVER_AGE, "XXXX-XXXX-0105", "ABCDE0105F",
                    Anchor.ENDED_5_DAYS_AGO),
            new SeedPolicy("S6", "customer2", "HOUSEHOLD", HOUSEHOLD_HOLDER_AGE, "XXXX-XXXX-0106", "ABCDE0106F",
                    Anchor.STARTED_30_DAYS_AGO));

    private final QuoteService quotes;
    private final ApplicationService applications;
    private final PolicyIssuanceService issuance;
    private final RenewalService renewal;
    private final RuleSetService ruleSets;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public PortfolioSeedService(QuoteService quotes, ApplicationService applications, PolicyIssuanceService issuance,
            RenewalService renewal, RuleSetService ruleSets, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.quotes = quotes;
        this.applications = applications;
        this.issuance = issuance;
        this.renewal = renewal;
        this.ruleSets = ruleSets;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** Creates the sample policies that are missing, then runs the renewal cycle for today. True when any was created. */
    public boolean seed() {
        LocalDate today = LocalDate.now(clock);
        int created = 0;
        for (SeedPolicy policy : POLICIES) {
            if (applications.hasApplicationWithAadhaar(policy.customerId(), policy.aadhaar())) {
                continue;
            }
            transaction.executeWithoutResult(status -> create(policy, today));
            created++;
        }
        if (created == 0) {
            LOG.info("Portfolio seed already present");
            return false;
        }
        renewal.runCycle(today, RenewalService.SYSTEM_ACTOR);
        LOG.info("Portfolio seeded policiesCreated={} runDate={}", created, today);
        return true;
    }

    /** One policy: quote, application and backdated issuance, in the caller's transaction. */
    private void create(SeedPolicy policy, LocalDate today) {
        int termMonths = ruleSets.activeVersion(policy.productCode()).lifecycle().termMonths();
        QuoteResult quote = quotes.create(policy.productCode(), riskInputs(policy.productCode()),
                policy.customerId());
        KycInput kyc = new KycInput(policy.fullName(), today.minusYears(policy.holderAge()).toString(),
                policy.aadhaar(), policy.pan(), new KycAddress(LINE1, CITY, POSTCODE));
        ApplicationResult application = applications.submit(
                new SubmitApplicationCommand(policy.customerId(), quote.quoteId(), kyc, List.of()));
        if (application.status() != ApplicationStatus.APPROVED) {
            throw new IllegalStateException("A seed application was not approved by the rules: seed=" + policy.name()
                    + " applicationId=" + application.applicationId() + " status=" + application.status());
        }
        PolicyHeader header = issuance.issueBackdated(policy.customerId(), application.applicationId(),
                effectiveDate(policy.anchor(), today, termMonths));
        LOG.info("Portfolio seed policy created seed={} policyNumber={} applicationId={} productCode={}",
                policy.name(), header.policyNumber(), application.applicationId(), policy.productCode());
    }

    /** The first term's start: the last day of cover is {@code start + termMonths - 1 day}. */
    private static LocalDate effectiveDate(Anchor anchor, LocalDate today, int termMonths) {
        return switch (anchor) {
            case STARTED_30_DAYS_AGO -> today.minusDays(30);
            case ENDS_IN_20_DAYS -> today.plusDays(20).plusDays(1).minusMonths(termMonths);
            case ENDED_5_DAYS_AGO -> today.minusDays(5).plusDays(1).minusMonths(termMonths);
        };
    }

    /** The AC-04.1, AC-01.2 and AC-01.3 profiles: premiums 6850.00, 15150.00 and 11350.00, no underwriting rule fires. */
    private static Map<String, Object> riskInputs(String productCode) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        switch (productCode) {
            case "TERM_LIFE" -> {
                inputs.put("age", TERM_LIFE_AGE);
                inputs.put("smoker", false);
                inputs.put("sumInsured", "5000000.00");
                inputs.put("termYears", 20);
                inputs.put("bmi", "24");
                inputs.put("healthDeclarations", List.of());
            }
            case "MOTOR" -> {
                inputs.put("vehicleValue", "800000.00");
                inputs.put("vehicleAgeYears", 2);
                inputs.put("driverAge", MOTOR_DRIVER_AGE);
                inputs.put("claimsLast3Years", 0);
                inputs.put("noClaimBonusYears", 2);
                inputs.put("vehicleUse", "PRIVATE");
            }
            case "HOUSEHOLD" -> {
                inputs.put("buildingSumInsured", "6000000.00");
                inputs.put("contentsSumInsured", "1000000.00");
                inputs.put("propertyAgeYears", 12);
                inputs.put("constructionType", "BRICK");
                inputs.put("floodZone", "LOW");
                inputs.put("hasSecuritySystem", true);
                inputs.put("hasFloodDefences", false);
            }
            default -> throw new IllegalArgumentException("No seed profile for product: " + productCode);
        }
        return inputs;
    }
}
