package com.horizon.policyforge.domain.ruleset;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** AC-02.4 at the domain level: the active version is the highest one whose effectiveFrom is on or before today. */
@Tag("AC-02")
class ActiveRuleSetResolverTest {

    private final ActiveRuleSetResolver resolver = new ActiveRuleSetResolver();

    private final RuleSetVersion v1 = RuleSets.motor(1, "2026-01-01");
    private final RuleSetVersion v2 = RuleSets.motor(2, "2027-01-01");

    private Integer activeOn(String today, RuleSetVersion... versions) {
        return resolver.resolve(List.of(versions), LocalDate.parse(today)).map(RuleSetVersion::version).orElse(null);
    }

    @Test
    @DisplayName("AC-02.4: with MOTOR v2 effective in the future, today resolves to v1; on v2's effectiveFrom it resolves to v2")
    void futureVersionIsIgnoredUntilItsEffectiveDate() {
        assertThat(activeOn("2026-10-28", v1, v2)).isEqualTo(1);
        assertThat(activeOn("2026-12-31", v1, v2)).isEqualTo(1);
        assertThat(activeOn("2027-01-01", v1, v2)).isEqualTo(2);
        assertThat(activeOn("2030-01-01", v1, v2)).isEqualTo(2);
    }

    @Test
    @DisplayName("AC-02.4: a version is active on its own effectiveFrom day and not the day before")
    void effectiveDateIsInclusive() {
        assertThat(activeOn("2026-01-01", v1)).isEqualTo(1);
        assertThat(activeOn("2025-12-31", v1)).isNull();
    }

    @Test
    @DisplayName("AC-02.4: no rule set when none is effective yet, regardless of the order the versions are given in")
    void noActiveVersionBeforeAnyIsEffective() {
        assertThat(resolver.resolve(List.of(), LocalDate.parse("2026-10-28"))).isEmpty();
        assertThat(activeOn("2025-06-01", v2, v1)).isNull();
        assertThat(activeOn("2027-06-01", v2, v1)).isEqualTo(2);
    }

    @Test
    @DisplayName("AC-02.4: the highest effective version wins even when a later version has an earlier-effective peer")
    void highestEffectiveVersionWins() {
        RuleSetVersion v3 = RuleSets.motor(3, "2026-06-01");
        assertThat(activeOn("2026-10-28", v1, v2, v3)).isEqualTo(3);
        assertThat(activeOn("2026-03-01", v1, v2, v3)).isEqualTo(1);
    }
}
