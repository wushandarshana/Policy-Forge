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
 * MOTOR: {@code vehicleValue x baseRate x vehicleAgeFactor x driverAgeFactor x (1 + claimLoading x claims)
 * x (1 - ncbDiscount) x useFactor + policyFee}. Every multiply rounds to scale 10 HALF_UP, and the premium is rounded
 * to scale 2 once, after the fee.
 */
public final class MotorRatingModel implements RatingAlgorithm {

    private static final String VEHICLE_VALUE = "vehicleValue";
    private static final String VEHICLE_AGE_YEARS = "vehicleAgeYears";
    private static final String DRIVER_AGE = "driverAge";
    private static final String CLAIMS_LAST_3_YEARS = "claimsLast3Years";
    private static final String NO_CLAIM_BONUS_YEARS = "noClaimBonusYears";
    private static final String VEHICLE_USE = "vehicleUse";

    /** The accepted inputs (AC-01.9); none are health inputs. */
    static final RiskInputSchema SCHEMA = new RiskInputSchema(
            List.of(VEHICLE_VALUE, VEHICLE_AGE_YEARS, DRIVER_AGE, CLAIMS_LAST_3_YEARS, NO_CLAIM_BONUS_YEARS, VEHICLE_USE),
            Set.of());

    @Override
    public RiskInputSchema schema() {
        return SCHEMA;
    }

    private record Inputs(BigDecimal vehicleValue, int vehicleAgeYears, int driverAge, int claims, String ncbYears,
            String vehicleUse) {
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

        BigDecimal baseRate = rating.scalar("baseRate");
        BigDecimal vehicleAge = RatingSupport.bandFactor(rating.bandTable("vehicleAgeFactor"), in.vehicleAgeYears(), "vehicleAgeFactor");
        BigDecimal driverAge = RatingSupport.bandFactor(rating.bandTable("driverAgeFactor"), in.driverAge(), "driverAgeFactor");
        BigDecimal claimLoading = rating.scalar("claimLoading");
        BigDecimal claims = BigDecimal.valueOf(in.claims());
        BigDecimal ncbDiscount = RatingSupport.keyed(rating.lookup("ncbDiscount"), in.ncbYears(), "ncbDiscount");
        BigDecimal useFactor = RatingSupport.keyed(rating.lookup("useFactor"), in.vehicleUse(), "useFactor");
        BigDecimal policyFee = rating.scalar("policyFee");

        BigDecimal premium = Money.intermediate(in.vehicleValue().multiply(baseRate));
        premium = Money.intermediate(premium.multiply(vehicleAge));
        premium = Money.intermediate(premium.multiply(driverAge));
        premium = Money.intermediate(premium.multiply(BigDecimal.ONE.add(claimLoading.multiply(claims))));
        premium = Money.intermediate(premium.multiply(BigDecimal.ONE.subtract(ncbDiscount)));
        premium = Money.intermediate(premium.multiply(useFactor));
        BigDecimal annual = Money.finalAmount(premium.add(policyFee));

        return new PremiumBreakdown(annual, in.vehicleValue(), List.of(
                new PremiumFactor("vehicleValue", in.vehicleValue()),
                new PremiumFactor("baseRate", baseRate),
                new PremiumFactor("vehicleAgeFactor", vehicleAge),
                new PremiumFactor("driverAgeFactor", driverAge),
                new PremiumFactor("claimLoading", claimLoading),
                new PremiumFactor("claimsLast3Years", claims),
                new PremiumFactor("ncbDiscount", ncbDiscount),
                new PremiumFactor("useFactor", useFactor),
                new PremiumFactor("policyFee", policyFee)));
    }

    private Optional<Inputs> read(RuleSetVersion rules, InputReader reader) {
        RatingFactors rating = rules.rating();
        reader.rejectAll(SCHEMA.unknownKeys(reader.raw()));
        Optional<BigDecimal> vehicleValue = reader.money(VEHICLE_VALUE);
        vehicleValue.ifPresent(value -> SumInsuredCheck.within(VEHICLE_VALUE, value, rules.sumInsured(), reader));
        Optional<Integer> vehicleAge = reader.wholeInBands(VEHICLE_AGE_YEARS, rating.bandTable("vehicleAgeFactor"));
        Optional<Integer> driverAge = reader.wholeInBands(DRIVER_AGE, rating.bandTable("driverAgeFactor"));
        Optional<Integer> claims = reader.wholeAtLeast(CLAIMS_LAST_3_YEARS, 0);
        Optional<String> ncbYears = reader.oneOf(NO_CLAIM_BONUS_YEARS, rating.lookup("ncbDiscount").keySet());
        Optional<String> vehicleUse = reader.oneOf(VEHICLE_USE, rating.lookup("useFactor").keySet());
        if (!reader.isValid()) {
            return Optional.empty();
        }
        return Optional.of(new Inputs(vehicleValue.get(), vehicleAge.get(), driverAge.get(), claims.get(),
                ncbYears.get(), vehicleUse.get()));
    }
}
