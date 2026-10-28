package com.horizon.policyforge.domain.rating;

import com.horizon.policyforge.domain.Money;
import com.horizon.policyforge.domain.ruleset.RatingFactors;
import com.horizon.policyforge.domain.ruleset.RuleSetVersion;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * HOUSEHOLD: {@code (buildingSumInsured x buildingRate + contentsSumInsured x contentsRate) x constructionFactor
 * x floodFactor x securityFactor + policyFee}. The sum insured is building plus contents. Every multiply rounds to
 * scale 10 HALF_UP, and the premium is rounded to scale 2 once, after the fee.
 */
public final class HouseholdRatingModel implements RatingAlgorithm {

    private static final String BUILDING_SUM_INSURED = "buildingSumInsured";
    private static final String CONTENTS_SUM_INSURED = "contentsSumInsured";
    private static final String PROPERTY_AGE_YEARS = "propertyAgeYears";
    private static final String CONSTRUCTION_TYPE = "constructionType";
    private static final String FLOOD_ZONE = "floodZone";
    private static final String HAS_SECURITY_SYSTEM = "hasSecuritySystem";
    private static final String HAS_FLOOD_DEFENCES = "hasFloodDefences";

    /** The accepted inputs (AC-01.9); none are health inputs. */
    static final RiskInputSchema SCHEMA = new RiskInputSchema(
            List.of(BUILDING_SUM_INSURED, CONTENTS_SUM_INSURED, PROPERTY_AGE_YEARS, CONSTRUCTION_TYPE, FLOOD_ZONE, HAS_SECURITY_SYSTEM, HAS_FLOOD_DEFENCES),
            Set.of());

    @Override
    public RiskInputSchema schema() {
        return SCHEMA;
    }

    private record Inputs(BigDecimal building, BigDecimal contents, String construction, String floodZone,
            boolean security) {
    }

    @Override
    public List<RiskInputViolation> validate(RuleSetVersion rules, Map<String, Object> riskInputs) {
        InputReader reader = new InputReader(riskInputs);
        read(rules, reader);
        return reader.violations();
    }

    @Override
    public PremiumBreakdown rate(RuleSetVersion rules, Map<String, Object> riskInputs) {
        InputReader reader = new InputReader(riskInputs);
        Optional<Inputs> read = read(rules, reader);
        if (read.isEmpty()) {
            throw new InvalidRiskInputException(reader.violations());
        }
        Inputs in = read.get();
        RatingFactors rating = rules.rating();

        BigDecimal buildingRate = rating.scalar("buildingRate");
        BigDecimal contentsRate = rating.scalar("contentsRate");
        BigDecimal construction = RatingSupport.keyed(rating.lookup("constructionFactor"), in.construction(), "constructionFactor");
        BigDecimal flood = RatingSupport.keyed(rating.lookup("floodFactor"), in.floodZone(), "floodFactor");
        BigDecimal security = RatingSupport.keyed(rating.lookup("securityFactor"), String.valueOf(in.security()), "securityFactor");
        BigDecimal policyFee = rating.scalar("policyFee");

        BigDecimal buildingPart = Money.intermediate(in.building().multiply(buildingRate));
        BigDecimal contentsPart = Money.intermediate(in.contents().multiply(contentsRate));
        BigDecimal premium = Money.intermediate(buildingPart.add(contentsPart));
        premium = Money.intermediate(premium.multiply(construction));
        premium = Money.intermediate(premium.multiply(flood));
        premium = Money.intermediate(premium.multiply(security));
        BigDecimal annual = Money.finalAmount(premium.add(policyFee));

        return new PremiumBreakdown(annual, Money.finalAmount(in.building().add(in.contents())), List.of(
                new PremiumFactor("buildingSumInsured", in.building()),
                new PremiumFactor("buildingRate", buildingRate),
                new PremiumFactor("contentsSumInsured", in.contents()),
                new PremiumFactor("contentsRate", contentsRate),
                new PremiumFactor("constructionFactor", construction),
                new PremiumFactor("floodFactor", flood),
                new PremiumFactor("securityFactor", security),
                new PremiumFactor("policyFee", policyFee)));
    }

    private Optional<Inputs> read(RuleSetVersion rules, InputReader reader) {
        RatingFactors rating = rules.rating();
        reader.rejectAll(SCHEMA.unknownKeys(reader.raw()));
        Optional<BigDecimal> building = reader.money(BUILDING_SUM_INSURED);
        Optional<BigDecimal> contents = reader.money(CONTENTS_SUM_INSURED);
        if (building.isPresent() && contents.isPresent()) {
            SumInsuredCheck.within("sumInsured", building.get().add(contents.get()), rules.sumInsured(), reader);
        }
        reader.wholeAtLeast(PROPERTY_AGE_YEARS, 0);
        Optional<String> construction = reader.oneOf(CONSTRUCTION_TYPE, rating.lookup("constructionFactor").keySet());
        Optional<String> floodZone = reader.oneOf(FLOOD_ZONE, rating.lookup("floodFactor").keySet());
        Optional<Boolean> security = reader.bool(HAS_SECURITY_SYSTEM);
        reader.bool(HAS_FLOOD_DEFENCES);
        if (!reader.isValid()) {
            return Optional.empty();
        }
        return Optional.of(new Inputs(building.get(), contents.get(), construction.get(), floodZone.get(), security.get()));
    }
}
