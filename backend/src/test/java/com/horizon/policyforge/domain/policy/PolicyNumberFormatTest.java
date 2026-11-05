package com.horizon.policyforge.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.horizon.policyforge.domain.PolicyForgeDomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Policy number format {@code PF-<TL|MO|HH>-<YYYY>-<NNNNNN>} (AC-05.1). */
@Tag("AC-05")
class PolicyNumberFormatTest {

    @Test
    @Tag("AC-05.1")
    @DisplayName("AC-05.1: (TERM_LIFE, 2026, 1) formats as PF-TL-2026-000001 and matches the spec pattern")
    void termLifeNumber() {
        String number = PolicyNumberFormat.format("TERM_LIFE", 2026, 1);

        assertThat(number).isEqualTo("PF-TL-2026-000001");
        assertThat(number).matches("^PF-TL-2026-\\d{6}$");
    }

    @ParameterizedTest(name = "AC-05.1: {0} maps to prefix {1}")
    @Tag("AC-05.1")
    @CsvSource({"TERM_LIFE,PF-TL-2026-000042", "MOTOR,PF-MO-2026-000042", "HOUSEHOLD,PF-HH-2026-000042"})
    void productPrefixes(String product, String expected) {
        assertThat(PolicyNumberFormat.format(product, 2026, 42)).isEqualTo(expected);
    }

    @Test
    @Tag("AC-05.1")
    @DisplayName("AC-05.1: the sequence is zero padded to 6 digits and 1 and 999999 are the bounds")
    void sequenceBounds() {
        assertThat(PolicyNumberFormat.format("MOTOR", 2027, 999_999)).isEqualTo("PF-MO-2027-999999");
        assertThat(PolicyNumberFormat.format("HOUSEHOLD", 2027, 123)).isEqualTo("PF-HH-2027-000123");
    }

    @ParameterizedTest(name = "AC-05.1: sequence {0} is rejected")
    @Tag("AC-05.1")
    @ValueSource(ints = {0, -1, 1_000_000})
    void sequenceOutOfRange(int sequence) {
        assertThatThrownBy(() -> PolicyNumberFormat.format("TERM_LIFE", 2026, sequence))
                .isInstanceOf(InvalidPolicyNumberException.class)
                .isInstanceOf(PolicyForgeDomainException.class);
    }

    @Test
    @Tag("AC-05.1")
    @DisplayName("AC-05.1: an unknown or null product, or a year that is not 4 digits, is rejected")
    void unknownProductAndBadYear() {
        assertThatThrownBy(() -> PolicyNumberFormat.format("PET", 2026, 1))
                .isInstanceOf(InvalidPolicyNumberException.class);
        assertThatThrownBy(() -> PolicyNumberFormat.format(null, 2026, 1))
                .isInstanceOf(InvalidPolicyNumberException.class);
        assertThatThrownBy(() -> PolicyNumberFormat.format("TERM_LIFE", 999, 1))
                .isInstanceOf(InvalidPolicyNumberException.class);
        assertThatThrownBy(() -> PolicyNumberFormat.format("TERM_LIFE", 10_000, 1))
                .isInstanceOf(InvalidPolicyNumberException.class);
    }

    @ParameterizedTest(name = "AC-05.2: the sequence after {0} is {1}")
    @Tag("AC-05.2")
    @CsvSource({"0,1", "1,2", "999997,999998", "999998,999999"})
    void nextSequenceCountsUpToTheLastNumber(int lastValue, int expected) {
        assertThat(PolicyNumberFormat.nextSequence(lastValue)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "AC-05.2: a counter at {0} is exhausted and hands out no number")
    @Tag("AC-05.2")
    @ValueSource(ints = {999_999, 1_000_000})
    void nextSequenceRefusesAnExhaustedCounter(int lastValue) {
        assertThatThrownBy(() -> PolicyNumberFormat.nextSequence(lastValue))
                .isInstanceOf(PolicyNumberSequenceExhaustedException.class)
                .isInstanceOf(PolicyForgeDomainException.class);
    }
}
