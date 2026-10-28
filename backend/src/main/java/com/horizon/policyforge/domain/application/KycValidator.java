package com.horizon.policyforge.domain.application;

import com.horizon.policyforge.domain.PolicyForgeDomainException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * KYC stub validation (underwriting spec, "Application", U-4). The date is passed in: the domain has no clock. Every
 * message is fixed text and never contains the submitted value.
 */
public final class KycValidator {

    public static final String FORMAT_CODE = "KYC_INVALID";
    public static final String AGE_MISMATCH_CODE = "KYC_AGE_MISMATCH";
    public static final String DATE_OF_BIRTH_FIELD = "kyc.dateOfBirth";

    static final int MAX_FULL_NAME = 200;
    static final int MAX_LINE1 = 200;
    static final int MAX_CITY = 100;

    private static final Pattern AADHAAR = Pattern.compile("[X0-9]{4}-[X0-9]{4}-[0-9]{4}");
    private static final Pattern PAN = Pattern.compile("[A-Z]{5}[0-9]{4}[A-Z]");
    private static final Pattern POSTCODE = Pattern.compile("[0-9]{6}");

    /** Every format violation at once, in field order. */
    public List<KycViolation> validate(KycInput kyc, LocalDate today) {
        KycInput input = kyc == null ? new KycInput(null, null, null, null, null) : kyc;
        KycAddress address = input.address() == null ? new KycAddress(null, null, null) : input.address();
        List<KycViolation> found = new ArrayList<>();

        requiredText(found, "kyc.fullName", input.fullName(), MAX_FULL_NAME);
        dateOfBirth(found, input.dateOfBirth(), today);
        pattern(found, "kyc.aadhaar", input.aadhaar(), AADHAAR);
        pattern(found, "kyc.pan", input.pan(), PAN);
        requiredText(found, "kyc.address.line1", address.line1(), MAX_LINE1);
        requiredText(found, "kyc.address.city", address.city(), MAX_CITY);
        pattern(found, "kyc.address.postcode", address.postcode(), POSTCODE);
        return List.copyOf(found);
    }

    /** Age in completed years on {@code today}. */
    public static int ageInYears(LocalDate dateOfBirth, LocalDate today) {
        return Period.between(dateOfBirth, today).getYears();
    }

    /**
     * The age mismatch of U-4, if any: TERM_LIFE against {@code age}, MOTOR against {@code driverAge}, zero tolerance,
     * and no check for any other product. A date of birth that is not a valid date is left to {@link #validate}. A
     * quote without the whole-number age input fails closed with a domain exception.
     */
    public Optional<KycViolation> checkAge(
            String productCode, Map<String, Object> riskInputs, KycInput kyc, LocalDate today) {
        String ageField = ageFieldOf(productCode);
        if (ageField == null) {
            return Optional.empty();
        }
        Optional<LocalDate> dateOfBirth = parseDate(kyc == null ? null : kyc.dateOfBirth());
        if (dateOfBirth.isEmpty()) {
            return Optional.empty();
        }
        int quoteAge = wholeNumber(riskInputs, ageField);
        if (ageInYears(dateOfBirth.get(), today) == quoteAge) {
            return Optional.empty();
        }
        return Optional.of(new KycViolation(DATE_OF_BIRTH_FIELD, AGE_MISMATCH_CODE,
                "Date of birth does not match the age on the quote"));
    }

    private static String ageFieldOf(String productCode) {
        if ("TERM_LIFE".equals(productCode)) {
            return "age";
        }
        if ("MOTOR".equals(productCode)) {
            return "driverAge";
        }
        return null;
    }

    private static int wholeNumber(Map<String, Object> riskInputs, String field) {
        Object raw = riskInputs == null ? null : riskInputs.get(field);
        if (raw == null) {
            throw new PolicyForgeDomainException("The quote has no " + field + " input to check the date of birth against");
        }
        try {
            return new BigDecimal(raw.toString().strip()).intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new PolicyForgeDomainException("The quote " + field + " input is not a whole number");
        }
    }

    private static Optional<LocalDate> parseDate(String text) {
        if (text == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(text));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static void dateOfBirth(List<KycViolation> found, String text, LocalDate today) {
        Optional<LocalDate> parsed = parseDate(text);
        if (parsed.isEmpty()) {
            found.add(format(DATE_OF_BIRTH_FIELD, "must be a valid ISO date (yyyy-MM-dd)"));
        } else if (parsed.get().isAfter(today)) {
            found.add(format(DATE_OF_BIRTH_FIELD, "must not be in the future"));
        }
    }

    private static void requiredText(List<KycViolation> found, String field, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            found.add(format(field, "must not be blank"));
        } else if (value.length() > maxLength) {
            found.add(format(field, "must be at most " + maxLength + " characters"));
        }
    }

    private static void pattern(List<KycViolation> found, String field, String value, Pattern pattern) {
        if (value == null || !pattern.matcher(value).matches()) {
            found.add(format(field, "has an invalid format"));
        }
    }

    private static KycViolation format(String field, String message) {
        return new KycViolation(field, FORMAT_CODE, message);
    }
}
