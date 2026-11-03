package com.horizon.policyforge.domain.portfolio;

import com.horizon.policyforge.domain.Money;
import com.horizon.policyforge.domain.policy.PolicyStatus;
import com.horizon.policyforge.domain.renewal.RenewalPolicy;
import com.horizon.policyforge.domain.renewal.TermPeriod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** The pure portfolio figures of the admin dashboard (AC-11.2 to AC-11.4, P-11). */
public final class PortfolioRules {

    /** How many days ahead of asOf a term may end and still be in the renewal pipeline. */
    public static final int PIPELINE_DAYS = 60;

    private final RenewalPolicy renewalPolicy = new RenewalPolicy();

    /**
     * Premium collected for one product: the sum of paid term premiums plus the signed endorsement adjustments minus the
     * refunds, exact at scale 2.
     */
    public BigDecimal premiumCollected(Collection<BigDecimal> paidTermPremiums, Collection<BigDecimal> adjustments,
            Collection<BigDecimal> refunds) {
        return Money.finalAmount(sum(paidTermPremiums).add(sum(adjustments)).subtract(sum(refunds)));
    }

    /** The total of the per-product figures, exact at scale 2. */
    public BigDecimal total(Collection<BigDecimal> perProduct) {
        return Money.finalAmount(sum(perProduct));
    }

    /**
     * Whether an in-force policy is in the renewal pipeline: the paid term in force on {@code asOf} ends within
     * {@link #PIPELINE_DAYS} days of it.
     */
    public boolean inRenewalPipeline(PolicyStatus status, List<TermPeriod> terms, LocalDate asOf) {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(asOf, "asOf");
        if (!status.isInForce()) {
            return false;
        }
        LocalDate horizon = asOf.plusDays(PIPELINE_DAYS);
        return renewalPolicy.termInForce(terms, asOf).map(term -> !term.endDate().isAfter(horizon)).orElse(false);
    }

    /** Whether an in-force policy is in the lapse forecast: its current paid term ended before {@code asOf}. */
    public boolean inLapseForecast(PolicyStatus status, List<TermPeriod> terms, LocalDate asOf) {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(asOf, "asOf");
        if (!status.isInForce()) {
            return false;
        }
        return renewalPolicy.currentPaidTerm(terms).map(term -> term.endDate().isBefore(asOf)).orElse(false);
    }

    private static BigDecimal sum(Collection<BigDecimal> values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            total = total.add(value);
        }
        return total;
    }
}
