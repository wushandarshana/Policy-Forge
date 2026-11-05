package com.horizon.policyforge.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The one shared sanitiser for unknown keys echoed in error responses (P-14). */
class EchoedKeySanitiserTest {

    @Test
    @Tag("AC-05")
    @Tag("AC-05.1")
    @DisplayName("AC-05.1: a 5000-character key is cut to its first 64 characters")
    void longKeyIsCut() {
        String key = "k".repeat(10) + "abcdefghij".repeat(499);
        assertThat(key).hasSize(5000);
        assertThat(EchoedKeySanitiser.sanitise(key)).isEqualTo(key.substring(0, 64)).hasSize(64);
    }

    @Test
    @Tag("AC-05")
    @Tag("AC-05.1")
    @DisplayName("AC-05.1: a valid key, including one of exactly 64 characters, is unchanged")
    void validKeyUnchanged() {
        assertThat(EchoedKeySanitiser.sanitise("extra.field-1_x")).isEqualTo("extra.field-1_x");
        String exact = "a".repeat(64);
        assertThat(EchoedKeySanitiser.sanitise(exact)).isEqualTo(exact);
    }

    @Test
    @Tag("AC-06")
    @Tag("AC-06.1")
    @DisplayName("AC-06.1: <script> becomes _script_")
    void markupIsNeutralised() {
        assertThat(EchoedKeySanitiser.sanitise("<script>")).isEqualTo("_script_");
    }

    @Test
    @Tag("AC-06")
    @Tag("AC-06.1")
    @DisplayName("AC-06.1: spaces, quotes, newlines and non-ASCII characters each become _")
    void everyOtherCharacterBecomesUnderscore() {
        assertThat(EchoedKeySanitiser.sanitise("a b\"c'd\neéf")).isEqualTo("a_b_c_d_e_f");
        assertThat(EchoedKeySanitiser.sanitise("")).isEmpty();
        assertThat(EchoedKeySanitiser.sanitise(null)).isEmpty();
    }

    @Test
    @Tag("AC-06")
    @Tag("AC-06.1")
    @DisplayName("AC-06.1: a long key of invalid characters is cut to 64 underscores")
    void cutAndReplaced() {
        assertThat(EchoedKeySanitiser.sanitise("<".repeat(5000))).isEqualTo("_".repeat(64));
    }
}
