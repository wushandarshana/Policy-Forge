package com.horizon.policyforge.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The admin portfolio dashboard (contract C6, AC-11): what is in force per product, what has been collected, what is
 * renewing and what is likely to lapse, as of one date. It carries policy numbers, products, dates and money only: no
 * customer ID and no personal data (P-9).
 *
 * @param asOf                   the date the pipeline and the forecast are read at
 * @param activePoliciesByProduct the in-force policies of each of the three products, in the launch order (0 when none)
 * @param premiumCollected       per product, in the same order, then {@code total}; every value is at scale 2
 * @param renewalPipeline        sorted by term end, then policy number
 * @param lapseForecast          sorted by policy number
 */
public record PortfolioDashboard(
        LocalDate asOf,
        Map<String, Integer> activePoliciesByProduct,
        Map<String, BigDecimal> premiumCollected,
        List<PipelineEntry> renewalPipeline,
        LapseForecast lapseForecast) {

    public PortfolioDashboard {
        // Copies keep the launch order of the products, which an immutable Map.copyOf would not.
        activePoliciesByProduct = new LinkedHashMap<>(activePoliciesByProduct);
        premiumCollected = new LinkedHashMap<>(premiumCollected);
        renewalPipeline = List.copyOf(renewalPipeline);
    }

    /**
     * A policy whose paid term in force on {@code asOf} ends within 60 days of it. {@code renewalPremium} is the next
     * term's premium, or null when no offer exists, and {@code paid} is that term's paid flag (false with no offer).
     */
    public record PipelineEntry(String policyNumber, LocalDate termEnd, BigDecimal renewalPremium, boolean paid) {
    }

    /** The in-force policies whose current paid term ended before {@code asOf}: in grace now, lapsing if unpaid. */
    public record LapseForecast(int count, List<String> policies) {

        public LapseForecast {
            policies = List.copyOf(policies);
        }
    }
}
