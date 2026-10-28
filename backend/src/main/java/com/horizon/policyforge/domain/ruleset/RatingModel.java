package com.horizon.policyforge.domain.ruleset;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The three rating algorithms a rule set can select. Each model also declares the shape its {@code rating} section
 * must have, the underwriting code prefix and the fields underwriting conditions may read.
 */
public enum RatingModel {

    TERM_LIFE("UW-TL-",
            Set.of("policyFee"),
            Set.of("baseRatePerThousand"),
            Map.of("smokerFactor", Set.of("true", "false"), "termFactor", Set.of("10", "20", "30")),
            Set.of("age", "smoker", "sumInsured", "termYears", "bmi", "healthDeclarations")),

    MOTOR("UW-MO-",
            Set.of("baseRate", "claimLoading", "policyFee"),
            Set.of("vehicleAgeFactor", "driverAgeFactor"),
            Map.of("ncbDiscount", Set.of("0", "1", "2", "3", "4", "5"),
                    "useFactor", Set.of("PRIVATE", "COMMERCIAL")),
            Set.of("vehicleValue", "vehicleAgeYears", "driverAge", "claimsLast3Years", "noClaimBonusYears",
                    "vehicleUse", "sumInsured", "age")),

    HOUSEHOLD("UW-HH-",
            Set.of("buildingRate", "contentsRate", "policyFee"),
            Set.of(),
            Map.of("constructionFactor", Set.of("BRICK", "CONCRETE", "TIMBER"),
                    "floodFactor", Set.of("LOW", "MEDIUM", "HIGH"),
                    "securityFactor", Set.of("true", "false")),
            Set.of("buildingSumInsured", "contentsSumInsured", "propertyAgeYears", "constructionType", "floodZone",
                    "hasSecuritySystem", "hasFloodDefences", "sumInsured", "age"));

    private final String underwritingPrefix;
    private final Set<String> requiredScalars;
    private final Set<String> requiredBands;
    private final Map<String, Set<String>> requiredLookups;
    private final Set<String> underwritingFields;

    RatingModel(String underwritingPrefix, Set<String> requiredScalars, Set<String> requiredBands,
            Map<String, Set<String>> requiredLookups, Set<String> underwritingFields) {
        this.underwritingPrefix = underwritingPrefix;
        this.requiredScalars = requiredScalars;
        this.requiredBands = requiredBands;
        this.requiredLookups = requiredLookups;
        this.underwritingFields = underwritingFields;
    }

    /** Prefix every underwriting rule code of this model must start with, e.g. {@code UW-MO-}. */
    public String underwritingPrefix() {
        return underwritingPrefix;
    }

    /** Names of single-decimal factors (rates, fees) the rating section must carry. */
    public Set<String> requiredScalars() {
        return requiredScalars;
    }

    /** Names of banded factor tables the rating section must carry. */
    public Set<String> requiredBands() {
        return requiredBands;
    }

    /** Names of keyed factor tables and the keys each must at least contain. */
    public Map<String, Set<String>> requiredLookups() {
        return requiredLookups;
    }

    /** Risk-input and derived fields an underwriting condition may reference. */
    public Set<String> underwritingFields() {
        return underwritingFields;
    }

    /** Parses a rating model name exactly as written in a rule file; empty when it is not one of the known models. */
    public static Optional<RatingModel> parse(String name) {
        if (name == null) {
            return Optional.empty();
        }
        for (RatingModel model : values()) {
            if (model.name().equals(name)) {
                return Optional.of(model);
            }
        }
        return Optional.empty();
    }
}
