package com.horizon.policyforge.domain.cancellation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** AC-08.1 and plan G-4: the cancellation reason is one of a closed set of four, matched exactly. */
@Tag("AC-08")
class CancellationReasonTest {

    @Test
    @DisplayName("AC-08.1: the reasons are NO_LONGER_NEEDED, REPLACED_BY_OTHER_COVER, AFFORDABILITY and OTHER, and each parses to itself")
    void theClosedSetParses() {
        assertThat(Arrays.stream(CancellationReason.values()).map(Enum::name).toList())
                .containsExactly("NO_LONGER_NEEDED", "REPLACED_BY_OTHER_COVER", "AFFORDABILITY", "OTHER");
        for (CancellationReason reason : CancellationReason.values()) {
            assertThat(CancellationReason.parse(reason.name())).contains(reason);
        }
    }

    @ParameterizedTest(name = "AC-08.1: \"{0}\" is not a cancellation reason")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "no_longer_needed", "No_Longer_Needed", " OTHER", "OTHER ", "SYNTHETIC_REASON_BETA",
            "NO_LONGER_NEEDED,OTHER", "OTHER\n"})
    void anythingElseIsRefused(String text) {
        assertThat(CancellationReason.parse(text)).isEmpty();
    }

    @Test
    @DisplayName("AC-08.1: the API client lists the same four reasons as the domain")
    void clientAndDomainAgree() {
        List<String> domain = Arrays.stream(CancellationReason.values()).map(Enum::name).toList();
        assertThat(domain).containsExactlyElementsOf(com.horizon.policyforge.support.CancellationApi.REASONS);
    }
}
