package com.horizon.policyforge.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The U-7 mask: {@code *} repeated (length minus 4) times, then the last 4 characters. Used for the Aadhaar and PAN in the
 * underwriter queue (AC-09.1). Synthetic values only.
 */
@Tag("AC-09")
class PiiMaskerTest {

    @Test
    @DisplayName("AC-09.1: Aadhaar XXXX-XXXX-1234 is masked to **********1234")
    void aadhaarKeepsOnlyItsLastFourCharacters() {
        assertThat(PiiMasker.mask("XXXX-XXXX-1234")).isEqualTo("**********1234");
    }

    @Test
    @DisplayName("AC-09.1: PAN ABCDE1234F is masked to ******234F")
    void panKeepsOnlyItsLastFourCharacters() {
        assertThat(PiiMasker.mask("ABCDE1234F")).isEqualTo("******234F");
    }

    @ParameterizedTest(name = "AC-09.1: {0} is masked to {1}")
    @CsvSource({
        "12345,*2345",
        "123456,**3456",
        "ABCDEFGHIJ,******GHIJ",
    })
    void maskIsStarsForAllButTheLastFour(String value, String expected) {
        assertThat(PiiMasker.mask(value)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "AC-09.1: a value of {0} characters or fewer is masked completely")
    @ValueSource(strings = {"1234", "123", "1", ""})
    void shortValuesAreNeverRevealed(String value) {
        assertThat(PiiMasker.mask(value)).isEqualTo("*".repeat(value.length()));
    }

    @Test
    @DisplayName("AC-09.1: a missing value stays missing, so nothing is invented")
    void nullStaysNull() {
        assertThat(PiiMasker.mask(null)).isNull();
    }
}
