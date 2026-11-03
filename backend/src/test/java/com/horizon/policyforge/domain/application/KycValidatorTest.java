package com.horizon.policyforge.domain.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** KYC stub validation (AC-03.2, AC-03.4) with the U-4 rules and the no-PII-in-strings rule (P-7). Synthetic data only. */
class KycValidatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private final KycValidator validator = new KycValidator();

    private static KycInput valid() {
        return new KycInput("Asha Test", "1991-04-02", "XXXX-XXXX-1234", "ABCDE1234F",
                new KycAddress("1 Demo Street", "Pune", "411001"));
    }

    private static KycInput with(String fullName, String dob, String aadhaar, String pan, String line1, String city,
            String postcode) {
        return new KycInput(fullName, dob, aadhaar, pan, new KycAddress(line1, city, postcode));
    }

    private List<String> fields(KycInput kyc) {
        return validator.validate(kyc, TODAY).stream().map(KycViolation::field).toList();
    }

    @Test
    @Tag("AC-03")
    @DisplayName("AC-03.2: PAN abcde1234f, Aadhaar 1234-5678 and postcode 41100 give exactly three violations")
    void invalidPanAadhaarAndPostcodeAreAllReported() {
        KycInput kyc = with("Asha Test", "1991-04-02", "1234-5678", "abcde1234f", "1 Demo Street", "Pune", "41100");

        List<KycViolation> violations = validator.validate(kyc, TODAY);

        assertThat(violations).extracting(KycViolation::field)
                .containsExactlyInAnyOrder("kyc.pan", "kyc.aadhaar", "kyc.address.postcode");
        assertThat(violations).hasSize(3);
        assertThat(violations).extracting(KycViolation::code).containsOnly(KycValidator.FORMAT_CODE);
    }

    @Test
    @Tag("AC-03")
    @DisplayName("AC-03.2: the synthetic KYC XXXX-XXXX-1234, ABCDE1234F, 411001 gives no violation")
    void syntheticKycIsAccepted() {
        assertThat(validator.validate(valid(), TODAY)).isEmpty();
    }

    @Test
    @Tag("AC-03")
    @DisplayName("AC-03.2: violation messages never contain the submitted value")
    void messagesNeverContainSubmittedValues() {
        KycInput kyc = with("   ", "not-a-date", "1234-5678", "abcde1234f", "", "", "41100");

        List<KycViolation> violations = validator.validate(kyc, TODAY);

        assertThat(violations).isNotEmpty();
        for (KycViolation v : violations) {
            assertThat(v.message()).doesNotContain("1234-5678", "abcde1234f", "not-a-date", "41100");
        }
    }

    @ParameterizedTest
    @Tag("AC-03")
    @ValueSource(strings = {"0000-0000-1234", "XXXX-XXXX-1234", "XXXX-1234-5678"})
    @DisplayName("AC-03.2: Aadhaar shapes with X or digits in the first two groups are accepted")
    void aadhaarAcceptsXOrDigitsInFirstTwoGroups(String aadhaar) {
        assertThat(fields(with("Asha Test", "1991-04-02", aadhaar, "ABCDE1234F", "1 Demo Street", "Pune", "411001")))
                .isEmpty();
    }

    @ParameterizedTest
    @Tag("AC-03")
    @ValueSource(strings = {"XXXX-XXXX-123X", "XXXX-XXXX-12345", "XXXXXXXXXXXX", "xxxx-xxxx-1234", "XXXX-XXXX-1234\n",
            "", "XXXX-XXXX-١٢٣٤"})
    @DisplayName("AC-03.2: malformed Aadhaar values name kyc.aadhaar")
    void malformedAadhaarIsRejected(String aadhaar) {
        assertThat(fields(with("Asha Test", "1991-04-02", aadhaar, "ABCDE1234F", "1 Demo Street", "Pune", "411001")))
                .containsExactly("kyc.aadhaar");
    }

    @ParameterizedTest
    @Tag("AC-03")
    @ValueSource(strings = {"ABCDE1234", "ABCDE12345", "ABCD1234FG", "abcde1234f", "ABCDE1234F\n", "ABCDE1234FF", ""})
    @DisplayName("AC-03.2: malformed PAN values name kyc.pan")
    void malformedPanIsRejected(String pan) {
        assertThat(fields(with("Asha Test", "1991-04-02", "XXXX-XXXX-1234", pan, "1 Demo Street", "Pune", "411001")))
                .containsExactly("kyc.pan");
    }

    @ParameterizedTest
    @Tag("AC-03")
    @ValueSource(strings = {"41100", "4110011", "41100A", "41 001", "411001\n", ""})
    @DisplayName("AC-03.2: a postcode that is not exactly 6 digits names kyc.address.postcode")
    void postcodeMustBeSixDigits(String postcode) {
        assertThat(fields(with("Asha Test", "1991-04-02", "XXXX-XXXX-1234", "ABCDE1234F", "1 Demo Street", "Pune",
                postcode))).containsExactly("kyc.address.postcode");
    }

    @Test
    @Tag("AC-03")
    @DisplayName("AC-03.2: blank or missing name, address line 1 and city are each reported")
    void requiredTextFieldsMustNotBeBlank() {
        assertThat(fields(with("  ", "1991-04-02", "XXXX-XXXX-1234", "ABCDE1234F", " ", "", "411001")))
                .containsExactlyInAnyOrder("kyc.fullName", "kyc.address.line1", "kyc.address.city");
        assertThat(fields(with(null, "1991-04-02", "XXXX-XXXX-1234", "ABCDE1234F", null, null, "411001")))
                .containsExactlyInAnyOrder("kyc.fullName", "kyc.address.line1", "kyc.address.city");
    }

    @Test
    @Tag("AC-03")
    @DisplayName("AC-03.2: a missing address or missing kyc reports every address and identity field")
    void missingAddressAndMissingKycAreReportedPerField() {
        assertThat(fields(new KycInput("Asha Test", "1991-04-02", "XXXX-XXXX-1234", "ABCDE1234F", null)))
                .containsExactlyInAnyOrder("kyc.address.line1", "kyc.address.city", "kyc.address.postcode");
        assertThat(fields(null)).containsExactlyInAnyOrder("kyc.fullName", "kyc.dateOfBirth", "kyc.aadhaar",
                "kyc.pan", "kyc.address.line1", "kyc.address.city", "kyc.address.postcode");
    }

    @Test
    @Tag("AC-03")
    @DisplayName("AC-03.2: maximum lengths are fullName 200, line1 200 and city 100 (U-4)")
    void maximumLengthsAreEnforced() {
        String s200 = "a".repeat(200);
        String s201 = "a".repeat(201);
        String s100 = "a".repeat(100);
        String s101 = "a".repeat(101);

        assertThat(fields(with(s200, "1991-04-02", "XXXX-XXXX-1234", "ABCDE1234F", s200, s100, "411001"))).isEmpty();
        assertThat(fields(with(s201, "1991-04-02", "XXXX-XXXX-1234", "ABCDE1234F", s201, s101, "411001")))
                .containsExactlyInAnyOrder("kyc.fullName", "kyc.address.line1", "kyc.address.city");
    }

    @ParameterizedTest
    @Tag("AC-03")
    @ValueSource(strings = {"", "02/04/1991", "1991-4-2", "1991-02-30", "2026-13-01", "not-a-date", "2026-10-29",
            "1991-04-02T00:00:00"})
    @DisplayName("AC-03.2: a date of birth that is not a valid ISO date, or is in the future, names kyc.dateOfBirth")
    void invalidOrFutureDateOfBirthIsRejected(String dob) {
        assertThat(fields(with("Asha Test", dob, "XXXX-XXXX-1234", "ABCDE1234F", "1 Demo Street", "Pune", "411001")))
                .containsExactly("kyc.dateOfBirth");
    }

    @Test
    @Tag("AC-03")
    @DisplayName("AC-03.2: a date of birth of today is accepted and a missing one is reported")
    void todayIsNotInTheFuture() {
        assertThat(fields(with("Asha Test", "2026-10-28", "XXXX-XXXX-1234", "ABCDE1234F", "1 Demo Street", "Pune",
                "411001"))).isEmpty();
        assertThat(fields(with("Asha Test", null, "XXXX-XXXX-1234", "ABCDE1234F", "1 Demo Street", "Pune", "411001")))
                .containsExactly("kyc.dateOfBirth");
    }

    @Test
    @Tag("AC-03")
    @DisplayName("AC-03.4: a date of birth giving age 40 against a quote age of 35 is KYC_AGE_MISMATCH")
    void ageMismatchIsReportedAtDateOfBirth() {
        KycInput kyc = with("Asha Test", "1986-04-02", "XXXX-XXXX-1234", "ABCDE1234F", "1 Demo Street", "Pune",
                "411001");

        assertThat(KycValidator.ageInYears(LocalDate.of(1986, 4, 2), TODAY)).isEqualTo(40);
        Optional<KycViolation> mismatch = validator.checkAge("TERM_LIFE", Map.of("age", 35), kyc, TODAY);

        assertThat(mismatch).isPresent();
        assertThat(mismatch.get().field()).isEqualTo("kyc.dateOfBirth");
        assertThat(mismatch.get().code()).isEqualTo("KYC_AGE_MISMATCH");
        assertThat(mismatch.get().message()).doesNotContain("1986", "40", "35");
    }

    @Test
    @Tag("AC-03")
    @DisplayName("AC-03.4: the day before the 36th birthday matches age 35, and the day of it does not")
    void birthdayBoundary() {
        LocalDate today = LocalDate.of(2026, 10, 28);
        LocalDate dayBefore36th = today.minusYears(36).plusDays(1);
        LocalDate dayOf36th = today.minusYears(36);

        assertThat(KycValidator.ageInYears(dayBefore36th, today)).isEqualTo(35);
        assertThat(KycValidator.ageInYears(dayOf36th, today)).isEqualTo(36);
        assertThat(validator.checkAge("TERM_LIFE", Map.of("age", 35), kycBorn(dayBefore36th), today)).isEmpty();
        assertThat(validator.checkAge("TERM_LIFE", Map.of("age", 35), kycBorn(dayOf36th), today)).isPresent();
    }

    @Test
    @Tag("AC-03")
    @DisplayName("AC-03.4: a leap-day birthday completes its year on 1 March in a common year")
    void leapDayBirthday() {
        LocalDate born = LocalDate.of(2000, 2, 29);

        assertThat(KycValidator.ageInYears(born, LocalDate.of(2026, 2, 28))).isEqualTo(25);
        assertThat(KycValidator.ageInYears(born, LocalDate.of(2026, 3, 1))).isEqualTo(26);
    }

    @Test
    @Tag("AC-03")
    @DisplayName("AC-03.4: MOTOR is checked against driverAge with zero tolerance and HOUSEHOLD has no age check")
    void ageIsCheckedPerProductWithZeroTolerance() {
        KycInput born1991 = kycBorn(LocalDate.of(1991, 4, 2)); // age 35 on 2026-10-28

        assertThat(validator.checkAge("MOTOR", Map.of("driverAge", 35), born1991, TODAY)).isEmpty();
        assertThat(validator.checkAge("MOTOR", Map.of("driverAge", 34), born1991, TODAY)).isPresent();
        assertThat(validator.checkAge("MOTOR", Map.of("driverAge", 36), born1991, TODAY)).isPresent();
        assertThat(validator.checkAge("TERM_LIFE", Map.of("age", 34), born1991, TODAY)).isPresent();
        assertThat(validator.checkAge("TERM_LIFE", Map.of("age", 35), born1991, TODAY)).isEmpty();
        assertThat(validator.checkAge("HOUSEHOLD", Map.of("age", 99, "driverAge", 99), born1991, TODAY)).isEmpty();
        // Quote inputs may arrive as text or BigDecimal; the comparison is on whole years.
        assertThat(validator.checkAge("TERM_LIFE", Map.of("age", "35"), born1991, TODAY)).isEmpty();
        assertThat(validator.checkAge("TERM_LIFE", Map.of("age", new java.math.BigDecimal("35")), born1991, TODAY))
                .isEmpty();
    }

    @Test
    @Tag("AC-03")
    @DisplayName("AC-03.4: the age check leaves an unparseable date of birth to the format check")
    void ageCheckSkipsUnparseableDateOfBirth() {
        KycInput kyc = with("Asha Test", "not-a-date", "XXXX-XXXX-1234", "ABCDE1234F", "1 Demo Street", "Pune",
                "411001");

        assertThat(validator.checkAge("TERM_LIFE", Map.of("age", 35), kyc, TODAY)).isEmpty();
    }

    @Test
    @Tag("AC-03")
    @DisplayName("AC-03.4: a quote without the age input fails closed with a domain exception, not a pass")
    void missingQuoteAgeFailsClosed() {
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> validator.checkAge("TERM_LIFE", Map.of(), valid(), TODAY))
                .isInstanceOf(com.horizon.policyforge.domain.PolicyForgeDomainException.class)
                .hasMessageNotContaining("1991");
    }

    @Test
    @Tag("AC-03")
    @DisplayName("AC-03.2: P-7 the string form of KYC types never shows name, date of birth, Aadhaar or PAN")
    void kycTypesRenderNoPii() {
        KycInput kyc = valid();
        String shown = kyc + " " + kyc.address();

        assertThat(shown).doesNotContain("Asha", "1991", "1234", "ABCDE", "Demo Street", "Pune", "411001");
    }

    private static KycInput kycBorn(LocalDate dob) {
        return with("Asha Test", dob.toString(), "XXXX-XXXX-1234", "ABCDE1234F", "1 Demo Street", "Pune", "411001");
    }
}
