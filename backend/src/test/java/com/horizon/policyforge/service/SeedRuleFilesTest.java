package com.horizon.policyforge.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.horizon.policyforge.domain.ruleset.RuleSetValidator;
import com.horizon.policyforge.domain.ruleset.RuleSetVersion;
import com.horizon.policyforge.service.RuleSetJsonParser.ParsedRuleSet;
import com.horizon.policyforge.support.RuleSetFixtures;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** The shipped v1 rule files pass the validator (the policy-version-validator checklist) and nothing newer ships. */
@Tag("AC-02")
@Tag("NFR-05")
class SeedRuleFilesTest {

    private static final Map<String, String> SLUG_TO_PRODUCT = Map.of(
            "term-life", "TERM_LIFE", "motor", "MOTOR", "household", "HOUSEHOLD");

    private final RuleSetJsonParser parser = new RuleSetJsonParser();
    private final RuleSetValidator validator = new RuleSetValidator();

    @Test
    @DisplayName("AC-02.5: each shipped v1 file is version 1, effective 2026-01-01, matches its folder and has no violations")
    void shippedFilesAreValid() {
        SLUG_TO_PRODUCT.forEach((slug, product) -> {
            ParsedRuleSet parsed = parser.parse(RuleSetFixtures.seedText(slug, 1));
            assertThat(parsed.violations()).as(slug + " parse").isEmpty();
            RuleSetVersion ruleSet = parsed.ruleSet();
            assertThat(validator.validate(ruleSet)).as(slug + " validation").isEmpty();
            assertThat(ruleSet.productCode()).isEqualTo(product);
            assertThat(ruleSet.version()).isEqualTo(1);
            assertThat(ruleSet.effectiveFrom()).isEqualTo(LocalDate.parse("2026-01-01"));
            assertThat(ruleSet.ratingModel()).isEqualTo(product);
        });
    }

    @Test
    @DisplayName("AC-02.2: the shipped underwriting rules are exactly the underwriting spec's v1 codes")
    void shippedUnderwritingCodes() {
        assertThat(codes("term-life")).containsExactly("UW-TL-001", "UW-TL-002", "UW-TL-003", "UW-TL-004", "UW-TL-005");
        assertThat(codes("motor")).containsExactly("UW-MO-001", "UW-MO-002", "UW-MO-003", "UW-MO-004");
        assertThat(codes("household")).containsExactly("UW-HH-001", "UW-HH-002", "UW-HH-003", "UW-HH-004");
    }

    @Test
    @DisplayName("AC-02.2: the shipped lifecycle values are the catalog launch table's")
    void shippedLifecycle() {
        assertLifecycle("term-life", 30, 15, "500.00");
        assertLifecycle("motor", 15, 7, "250.00");
        assertLifecycle("household", 15, 14, "200.00");
    }

    @Test
    @DisplayName("AC-02.1: no seed file newer than v1 ships, so every product starts at active version 1 (J6)")
    void noVersionTwoSeedFiles() throws IOException {
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath*:rules/*/v*.json");
        List<String> names = Arrays.stream(files).map(Resource::getFilename).toList();

        assertThat(names).hasSize(3).containsOnly("v1.json");
        for (Resource file : files) {
            assertThat(file.getContentAsString(StandardCharsets.UTF_8)).contains("\"version\": 1");
        }
    }

    private List<String> codes(String slug) {
        return parser.parse(RuleSetFixtures.seedText(slug, 1)).ruleSet().underwritingRules().stream()
                .map(rule -> rule.code()).toList();
    }

    private void assertLifecycle(String slug, int grace, int coolingOff, String fee) {
        var lifecycle = parser.parse(RuleSetFixtures.seedText(slug, 1)).ruleSet().lifecycle();
        assertThat(lifecycle.gracePeriodDays()).isEqualTo(grace);
        assertThat(lifecycle.coolingOffDays()).isEqualTo(coolingOff);
        assertThat(lifecycle.cancellationFee()).isEqualByComparingTo(fee);
        assertThat(lifecycle.termMonths()).isEqualTo(12);
        assertThat(lifecycle.renewalOfferDaysBeforeExpiry()).isEqualTo(30);
        assertThat(lifecycle.quoteValidityDays()).isEqualTo(30);
    }
}
