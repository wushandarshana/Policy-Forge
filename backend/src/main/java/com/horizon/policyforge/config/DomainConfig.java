package com.horizon.policyforge.config;

import com.horizon.policyforge.domain.rating.PremiumCalculator;
import com.horizon.policyforge.domain.ruleset.ActiveRuleSetResolver;
import com.horizon.policyforge.domain.ruleset.RuleSetValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the stateless, pure domain rule classes as beans. The domain itself stays free of Spring annotations. */
@Configuration
public class DomainConfig {

    @Bean
    public RuleSetValidator ruleSetValidator() {
        return new RuleSetValidator();
    }

    @Bean
    public ActiveRuleSetResolver activeRuleSetResolver() {
        return new ActiveRuleSetResolver();
    }

    @Bean
    public PremiumCalculator premiumCalculator() {
        return new PremiumCalculator();
    }
}
