package com.horizon.policyforge.domain.portfolio;

import com.horizon.policyforge.domain.policy.PolicyStatus;
import com.horizon.policyforge.domain.renewal.TermPeriod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/** The pure portfolio figures of the admin dashboard (AC-11.2 to AC-11.4, P-11). */
public final class PortfolioRules {

    /** How many days ahead of asOf a term may end and still be in the renewal pipeline. */
    public static final int PIPELINE_DAYS = 60;

    /** Premium collected for one product: paid term premiums plus adjustments minus refunds, scale 2. */
    public BigDecimal premiumCollected(Collection<BigDecimal> paidTermPremiums, Collection<BigDecimal> adjustments,
            Collection<BigDecimal> refunds) {
        return BigDecimal.ZERO;
    }

    /** The total of the per-product figures, scale 2. */
    public BigDecimal total(Collection<BigDecimal> perProduct) {
        return BigDecimal.ZERO;
    }

    /** Whether the policy is in the renewal pipeline on {@code asOf}. */
    public boolean inRenewalPipeline(PolicyStatus status, List<TermPeriod> terms, LocalDate asOf) {
        return false;
    }

    /** Whether the policy is in the lapse forecast on {@code asOf}. */
    public boolean inLapseForecast(PolicyStatus status, List<TermPeriod> terms, LocalDate asOf) {
        return false;
    }
}
