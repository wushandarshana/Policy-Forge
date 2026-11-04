package com.horizon.policyforge.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The policy term aggregate (AC-07.3, AC-07.5): its rule version is fixed at construction and a paid term stays paid. */
@Tag("AC-07")
class PolicyTermEntityTest {

    private static PolicyTermEntity term(boolean paid, int ruleVersion) {
        return PolicyTermEntity.of("PF-TL-2026-000001", 2, LocalDate.parse("2027-11-01"),
                LocalDate.parse("2028-10-31"), new BigDecimal("7400.00"), paid, ruleVersion);
    }

    @Test
    @DisplayName("AC-07.3: a term records the rule version it was built with, and has no setter that could change it")
    void theRuleVersionIsSetAtConstruction() {
        PolicyTermEntity term = term(false, 2);

        assertThat(term.getRuleVersion()).isEqualTo(2);
        assertThat(Arrays.stream(PolicyTermEntity.class.getMethods()).map(Method::getName)
                .filter(name -> name.startsWith("set"))).as("no setter of any field").isEmpty();
        assertThat(Arrays.stream(PolicyTermEntity.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .filter(field -> Modifier.isPublic(field.getModifiers()))
                .map(Field::getName)).as("no public field").isEmpty();
    }

    @Test
    @DisplayName("AC-07.5: marking an unpaid term paid sets the flag, and marking an already-paid term paid is refused")
    void anAlreadyPaidTermCannotBePaidAgain() {
        PolicyTermEntity unpaid = term(false, 1);

        unpaid.markPaid();

        assertThat(unpaid.isPaid()).isTrue();
        assertThatThrownBy(unpaid::markPaid).isInstanceOf(IllegalStateException.class);
        assertThat(unpaid.isPaid()).as("a paid term never becomes unpaid").isTrue();
        assertThatThrownBy(() -> term(true, 1).markPaid()).as("term 1 is created paid, so it cannot be paid again")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("AC-07.3: the string form of a term shows the policy number and term number only")
    void theStringFormShowsIdsOnly() {
        assertThat(term(false, 2).toString()).contains("PF-TL-2026-000001").doesNotContain("7400");
    }
}
