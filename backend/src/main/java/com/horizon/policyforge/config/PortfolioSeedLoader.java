package com.horizon.policyforge.config;

import com.horizon.policyforge.service.PortfolioSeedService;
import java.io.IOException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * On startup, seeds the synthetic sample portfolio (app_spec section 9, plan C10): six policies, two per product, for
 * {@code customer1} and {@code customer2}, followed by one renewal cycle for today, so the admin dashboard is never
 * empty in a demo. It runs after the underwriting seed ({@link ApplicationSeedLoader}) and in every application
 * context. The work is {@link PortfolioSeedService}'s and goes through the real quote, application and issuance
 * services; it is idempotent, so a restart adds nothing.
 */
@Component
@Order(PortfolioSeedLoader.ORDER)
public class PortfolioSeedLoader implements ApplicationRunner {

    /** After {@link ApplicationSeedLoader#ORDER}. */
    static final int ORDER = ApplicationSeedLoader.ORDER + 10;

    private final RuleSetSeedLoader ruleSetSeed;
    private final PortfolioSeedService seeder;

    public PortfolioSeedLoader(RuleSetSeedLoader ruleSetSeed, PortfolioSeedService seeder) {
        this.ruleSetSeed = ruleSetSeed;
        this.seeder = seeder;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        seed();
    }

    /** Creates the sample portfolio unless it exists. Returns true when anything was created. */
    public boolean seed() throws IOException {
        // The rule files are loaded idempotently, so this is safe whichever runner the framework starts first.
        ruleSetSeed.load();
        return seeder.seed();
    }
}
