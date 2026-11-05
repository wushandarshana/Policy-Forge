package com.horizon.policyforge.domain.renewal;

import static org.assertj.core.api.Assertions.assertThat;

import com.horizon.policyforge.domain.ruleset.RuleSetVersion;
import com.horizon.policyforge.service.RuleSetJsonParser;
import com.horizon.policyforge.support.RuleSetFixtures;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The lapse rule (AC-07.4) and the per-product grace periods of the shipped v1 rule files (AC-07.6). */
@Tag("AC-07")
class GracePeriodPolicyTest {

    private final GracePeriodPolicy policy = new GracePeriodPolicy();

    private static int graceDays(String slug) {
        RuleSetJsonParser.ParsedRuleSet parsed = new RuleSetJsonParser().parse(RuleSetFixtures.seedText(slug, 1));
        assertThat(parsed.violations()).isEmpty();
        RuleSetVersion ruleSet = parsed.ruleSet();
        return ruleSet.lifecycle().gracePeriodDays();
    }

    @Test
    @Tag("AC-07.4")
    @DisplayName("AC-07.4: term end 2027-10-31, grace 30, unpaid: 2027-11-30 is not lapsed and 2027-12-01 is lapsed")
    void unpaidLapsesTheDayAfterGrace() {
        LocalDate termEnd = LocalDate.of(2027, 10, 31);
        assertThat(policy.isLapsed(termEnd, 30, LocalDate.of(2027, 11, 30), false)).isFalse();
        assertThat(policy.isLapsed(termEnd, 30, LocalDate.of(2027, 12, 1), false)).isTrue();
    }

    @Test
    @Tag("AC-07.4")
    @DisplayName("AC-07.4: a paid term never lapses, however late the run date")
    void paidNeverLapses() {
        LocalDate termEnd = LocalDate.of(2027, 10, 31);
        assertThat(policy.isLapsed(termEnd, 30, LocalDate.of(2027, 12, 1), true)).isFalse();
        assertThat(policy.isLapsed(termEnd, 30, LocalDate.of(2035, 1, 1), true)).isFalse();
    }

    @Test
    @Tag("AC-07.4")
    @DisplayName("AC-07.4: an unpaid term is not lapsed before or on its end date")
    void notLapsedBeforeTermEnd() {
        LocalDate termEnd = LocalDate.of(2027, 10, 31);
        assertThat(policy.isLapsed(termEnd, 0, termEnd, false)).isFalse();
        assertThat(policy.isLapsed(termEnd, 0, termEnd.plusDays(1), false)).isTrue();
        assertThat(policy.isLapsed(termEnd, 30, termEnd.minusDays(5), false)).isFalse();
    }

    @Test
    @Tag("AC-07.6")
    @DisplayName("AC-07.6: MOTOR and HOUSEHOLD (grace 15) are first lapsed on termEnd + 16, not on termEnd + 15")
    void motorAndHouseholdGraceIsFifteen() {
        LocalDate termEnd = LocalDate.of(2027, 10, 31);
        for (String slug : new String[] {"motor", "household"}) {
            int grace = graceDays(slug);
            assertThat(grace).as(slug).isEqualTo(15);
            assertThat(policy.isLapsed(termEnd, grace, termEnd.plusDays(15), false)).as(slug + " +15").isFalse();
            assertThat(policy.isLapsed(termEnd, grace, termEnd.plusDays(16), false)).as(slug + " +16").isTrue();
        }
    }

    @Test
    @Tag("AC-07.6")
    @DisplayName("AC-07.6: TERM_LIFE (grace 30) is first lapsed on termEnd + 31, not on termEnd + 30")
    void termLifeGraceIsThirty() {
        LocalDate termEnd = LocalDate.of(2027, 10, 31);
        int grace = graceDays("term-life");
        assertThat(grace).isEqualTo(30);
        assertThat(policy.isLapsed(termEnd, grace, termEnd.plusDays(30), false)).isFalse();
        assertThat(policy.isLapsed(termEnd, grace, termEnd.plusDays(31), false)).isTrue();
    }
}
