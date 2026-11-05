package com.horizon.policyforge.config;

import com.horizon.policyforge.domain.application.KycAddress;
import com.horizon.policyforge.domain.application.KycInput;
import com.horizon.policyforge.service.ApplicationResult;
import com.horizon.policyforge.service.ApplicationService;
import com.horizon.policyforge.service.QuoteResult;
import com.horizon.policyforge.service.QuoteService;
import com.horizon.policyforge.service.SubmitApplicationCommand;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * On startup, seeds one synthetic REFERRED TERM_LIFE application for {@code customer2} (underwriting spec U-14, app_spec
 * section 9), so the underwriter workbench queue is never empty in a demo.
 *
 * <p>The seed goes through the same quote and application services as a real submission (P-5): it quotes a smoker with
 * sum insured 12,000,000.00, then applies as customer2 with synthetic KYC whose date of birth matches the quote's age on
 * the injected clock. It therefore gets a {@code RULES} decision row by {@code system} at the active rule version and
 * the two U-12 audit entries, with no special-case writes. The quote is created as owned by customer2 (P-8). It is
 * idempotent: an application of customer2 carrying the
 * seed's synthetic Aadhaar means it already ran, so a restart adds nothing.
 */
@Component
@Order(ApplicationSeedLoader.ORDER)
public class ApplicationSeedLoader implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationSeedLoader.class);

    /** Runs before the portfolio seed ({@link PortfolioSeedLoader}). */
    static final int ORDER = 10;
    static final String CUSTOMER = "customer2";
    /** Synthetic and distinctive, so it doubles as the marker that the seed already ran. */
    static final String SEED_AADHAAR = "XXXX-XXXX-0002";
    private static final String SEED_PAN = "ABCDE0002F";
    private static final int SEED_AGE = 40;

    private final RuleSetSeedLoader ruleSetSeed;
    private final QuoteService quotes;
    private final ApplicationService applications;
    private final Clock clock;

    public ApplicationSeedLoader(RuleSetSeedLoader ruleSetSeed, QuoteService quotes, ApplicationService applications,
            Clock clock) {
        this.ruleSetSeed = ruleSetSeed;
        this.quotes = quotes;
        this.applications = applications;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        seed();
    }

    /** Creates the seed application unless it exists. Returns true when one was created. */
    public boolean seed() throws IOException {
        // The rule files are loaded idempotently, so this is safe whichever runner the framework starts first.
        ruleSetSeed.load();
        if (applications.hasApplicationWithAadhaar(CUSTOMER, SEED_AADHAAR)) {
            LOG.info("Application seed already present");
            return false;
        }
        LocalDate today = LocalDate.now(clock);
        QuoteResult quote = quotes.create("TERM_LIFE", seedRiskInputs(), CUSTOMER);
        KycInput kyc = new KycInput("Demo Customer Two", today.minusYears(SEED_AGE).toString(), SEED_AADHAAR, SEED_PAN,
                new KycAddress("2 Demo Street", "Mumbai", "400001"));
        ApplicationResult seeded = applications.submit(
                new SubmitApplicationCommand(CUSTOMER, quote.quoteId(), kyc, List.of()));
        LOG.info("Application seeded applicationId={} quoteId={} status={}", seeded.applicationId(), seeded.quoteId(),
                seeded.status());
        return true;
    }

    /** A smoker with sum insured 12,000,000.00, so only UW-TL-002 fires (REFER). */
    private static Map<String, Object> seedRiskInputs() {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("age", SEED_AGE);
        inputs.put("smoker", true);
        inputs.put("sumInsured", "12000000.00");
        inputs.put("termYears", 20);
        inputs.put("bmi", "24");
        inputs.put("healthDeclarations", List.of());
        return inputs;
    }
}
