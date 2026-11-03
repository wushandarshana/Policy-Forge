package com.horizon.policyforge.config;

import com.horizon.policyforge.domain.application.KycValidator;
import com.horizon.policyforge.domain.endorsement.EndorsementPremiumAdjuster;
import com.horizon.policyforge.domain.endorsement.EndorsementRequestParser;
import com.horizon.policyforge.domain.endorsement.EndorsementValidator;
import com.horizon.policyforge.domain.rating.PremiumCalculator;
import com.horizon.policyforge.domain.rating.RiskInputValidator;
import com.horizon.policyforge.domain.ruleset.ActiveRuleSetResolver;
import com.horizon.policyforge.domain.ruleset.RuleSetValidator;
import com.horizon.policyforge.domain.underwriting.UnderwritingRuleEvaluator;
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

    @Bean
    public RiskInputValidator riskInputValidator() {
        return new RiskInputValidator();
    }

    @Bean
    public KycValidator kycValidator() {
        return new KycValidator();
    }

    @Bean
    public UnderwritingRuleEvaluator underwritingRuleEvaluator() {
        return new UnderwritingRuleEvaluator();
    }

    @Bean
    public EndorsementValidator endorsementValidator() {
        return new EndorsementValidator();
    }

    @Bean
    public EndorsementPremiumAdjuster endorsementPremiumAdjuster() {
        return new EndorsementPremiumAdjuster();
    }

    @Bean
    public EndorsementRequestParser endorsementRequestParser() {
        return new EndorsementRequestParser();
    }
}
