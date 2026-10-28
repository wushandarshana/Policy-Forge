package com.horizon.policyforge.domain.rating;

import com.horizon.policyforge.domain.Money;
import com.horizon.policyforge.domain.ruleset.RatingFactors;
import com.horizon.policyforge.domain.ruleset.RuleSetVersion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * TERM_LIFE: {@code sumInsured / 1000 x baseRatePerThousand[age] x smokerFactor x termFactor[termYears] + policyFee}.
 * Every multiply or divide rounds to scale 10 HALF_UP, and the premium is rounded to scale 2 once, after the fee.
 */
public final class TermLifeRatingModel implements RatingAlgorithm {

    private static final String AGE = "age";
    private static final String SMOKER = "smoker";
    private static final String SUM_INSURED = "sumInsured";
    private static final String TERM_YEARS = "termYears";
    private static final String BMI = "bmi";
    private static final String HEALTH_DECLARATIONS = "healthDeclarations";

    /** AC-01.10: healthDeclarations is at most this many codes. */
    static final int MAX_HEALTH_DECLARATIONS = 10;

    /** AC-01.10: each health declaration is an upper-case code of at most 64 characters, e.g. {@code DIABETES}. */
    static final Pattern HEALTH_DECLARATION_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");

    /** The accepted inputs (AC-01.9); health inputs are never shown publicly (NFR-04.2). */
    static final RiskInputSchema SCHEMA = new RiskInputSchema(
            List.of(AGE, SMOKER, SUM_INSURED, TERM_YEARS, BMI, HEALTH_DECLARATIONS),
            Set.of(BMI, HEALTH_DECLARATIONS));

    @Override
    public RiskInputSchema schema() {
        return SCHEMA;
    }

    private static final BigDecimal PER_THOUSAND = BigDecimal.valueOf(1000);

    private record Inputs(int age, boolean smoker, BigDecimal sumInsured, String termYears) {
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

        BigDecimal baseRate = RatingSupport.bandFactor(rating.bandTable("baseRatePerThousand"), in.age(), "baseRatePerThousand");
        BigDecimal smokerFactor = RatingSupport.keyed(rating.lookup("smokerFactor"), String.valueOf(in.smoker()), "smokerFactor");
        BigDecimal termFactor = RatingSupport.keyed(rating.lookup("termFactor"), in.termYears(), "termFactor");
        BigDecimal policyFee = rating.scalar("policyFee");

        BigDecimal premium = Money.intermediate(
                in.sumInsured().divide(PER_THOUSAND, Money.INTERMEDIATE_SCALE, RoundingMode.HALF_UP));
        premium = Money.intermediate(premium.multiply(baseRate));
        premium = Money.intermediate(premium.multiply(smokerFactor));
        premium = Money.intermediate(premium.multiply(termFactor));
        BigDecimal annual = Money.finalAmount(premium.add(policyFee));

        return new PremiumBreakdown(annual, in.sumInsured(), List.of(
                new PremiumFactor("sumInsured", in.sumInsured()),
                new PremiumFactor("baseRatePerThousand", baseRate),
                new PremiumFactor("smokerFactor", smokerFactor),
                new PremiumFactor("termFactor", termFactor),
                new PremiumFactor("policyFee", policyFee)));
    }

    private Optional<Inputs> read(RuleSetVersion rules, InputReader reader) {
        RatingFactors rating = rules.rating();
        reader.rejectAll(SCHEMA.unknownKeys(reader.raw()));
        Optional<Integer> age = reader.wholeInBands(AGE, rating.bandTable("baseRatePerThousand"));
        Optional<Boolean> smoker = reader.bool(SMOKER);
        Optional<BigDecimal> sumInsured = reader.money(SUM_INSURED);
        sumInsured.ifPresent(value -> SumInsuredCheck.within(SUM_INSURED, value, rules.sumInsured(), reader));
        Optional<String> termYears = reader.oneOf(TERM_YEARS, rating.lookup("termFactor").keySet());
        reader.positiveDecimal(BMI);
        reader.codeList(HEALTH_DECLARATIONS, MAX_HEALTH_DECLARATIONS, HEALTH_DECLARATION_CODE);
        if (!reader.isValid()) {
            return Optional.empty();
        }
        return Optional.of(new Inputs(age.get(), smoker.get(), sumInsured.get(), termYears.get()));
    }
}
