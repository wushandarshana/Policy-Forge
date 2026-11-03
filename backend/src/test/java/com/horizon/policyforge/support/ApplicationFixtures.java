package com.horizon.policyforge.support;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Synthetic request bodies for the application and underwriting tests (root CLAUDE.md rule 4): dummy name, Aadhaar-
 * and PAN-shaped values, and risk-input profiles that fire exactly the underwriting rules each test names.
 */
public final class ApplicationFixtures {

    public static final String FULL_NAME = "Asha Test";
    public static final String AADHAAR = "XXXX-XXXX-1234";
    public static final String PAN = "ABCDE1234F";
    public static final String LINE1 = "1 Demo Street";
    public static final String CITY = "Pune";
    public static final String POSTCODE = "411001";
    /** A valid health declaration code that no log line may ever contain (NFR-03.1). */
    public static final String HEALTH_TEXT = "SYNTHETIC_DECLARATION_ALPHA";

    private ApplicationFixtures() {
    }

    /** TERM_LIFE inputs; {@code bmi} is decimal text and {@code health} may be empty. */
    public static Map<String, Object> termLifeInputs(
            int age, boolean smoker, String sumInsured, String bmi, List<String> health) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("age", age);
        inputs.put("smoker", smoker);
        inputs.put("sumInsured", sumInsured);
        inputs.put("termYears", 20);
        inputs.put("bmi", bmi);
        inputs.put("healthDeclarations", health);
        return inputs;
    }

    /** The AC-04.1 profile: age 35, non-smoker, BMI 24, no declarations, sum insured 5,000,000.00. */
    public static Map<String, Object> cleanTermLifeInputs() {
        return termLifeInputs(35, false, "5000000.00", "24", List.of());
    }

    /** MOTOR inputs for a driver of {@code driverAge} with {@code claims} claims in the last three years. */
    public static Map<String, Object> motorInputs(int driverAge, int claims) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("vehicleValue", "800000.00");
        inputs.put("vehicleAgeYears", 2);
        inputs.put("driverAge", driverAge);
        inputs.put("claimsLast3Years", claims);
        inputs.put("noClaimBonusYears", 2);
        inputs.put("vehicleUse", "PRIVATE");
        return inputs;
    }

    /** HOUSEHOLD inputs; every rule stays quiet unless {@code constructionType} is TIMBER. */
    public static Map<String, Object> householdInputs(String constructionType) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("buildingSumInsured", "6000000.00");
        inputs.put("contentsSumInsured", "1000000.00");
        inputs.put("propertyAgeYears", 12);
        inputs.put("constructionType", constructionType);
        inputs.put("floodZone", "LOW");
        inputs.put("hasSecuritySystem", true);
        inputs.put("hasFloodDefences", false);
        return inputs;
    }

    /** Valid synthetic KYC for a person born on {@code dateOfBirth}. The nested address map is mutable. */
    public static Map<String, Object> kyc(LocalDate dateOfBirth) {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("line1", LINE1);
        address.put("city", CITY);
        address.put("postcode", POSTCODE);
        Map<String, Object> kyc = new LinkedHashMap<>();
        kyc.put("fullName", FULL_NAME);
        kyc.put("dateOfBirth", dateOfBirth.toString());
        kyc.put("aadhaar", AADHAAR);
        kyc.put("pan", PAN);
        kyc.put("address", address);
        return kyc;
    }

    /** The application request body: exactly {@code {quoteId, kyc}} (U-1). */
    public static Map<String, Object> application(String quoteId, Map<String, Object> kyc) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("quoteId", quoteId);
        body.put("kyc", kyc);
        return body;
    }

    /** The nested address of a KYC map built by {@link #kyc}. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> addressOf(Map<String, Object> kyc) {
        return (Map<String, Object>) kyc.get("address");
    }
}
