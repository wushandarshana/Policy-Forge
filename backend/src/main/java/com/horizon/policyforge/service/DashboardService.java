package com.horizon.policyforge.service;

import com.horizon.policyforge.domain.portfolio.PortfolioRules;
import com.horizon.policyforge.domain.renewal.RenewalPolicy;
import com.horizon.policyforge.domain.renewal.TermPeriod;
import com.horizon.policyforge.repository.EndorsementEntity;
import com.horizon.policyforge.repository.EndorsementRepository;
import com.horizon.policyforge.repository.PolicyEntity;
import com.horizon.policyforge.repository.PolicyRepository;
import com.horizon.policyforge.repository.PolicyTermEntity;
import com.horizon.policyforge.repository.PolicyTermRepository;
import com.horizon.policyforge.repository.RefundRecordEntity;
import com.horizon.policyforge.repository.RefundRecordRepository;
import com.horizon.policyforge.service.PortfolioDashboard.LapseForecast;
import com.horizon.policyforge.service.PortfolioDashboard.PipelineEntry;
import com.horizon.policyforge.service.ValidationFailedException.FieldViolation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The admin portfolio dashboard use case (AC-11, contract C6, P-11): a read-only snapshot. Every figure is computed by
 * the domain's {@link PortfolioRules}; this class loads the policies with their terms, endorsements and refunds,
 * hands them over and assembles the answer.
 *
 * <p>The active count and the premium collected are taken from what is stored at request time (the status, every paid
 * term, every endorsement adjustment and every refund), whatever {@code asOf} is: {@code asOf} does not rewrite
 * history (SG-6). {@code asOf} moves only the pipeline and the forecast. All three launch products are always listed,
 * with 0 and 0.00 when a product has no policies. A read is not audited (P-10) and the answer holds no personal data
 * (P-9). It logs counts only (NFR-03).
 */
@Service
public class DashboardService {

    private static final Logger LOG = LoggerFactory.getLogger(DashboardService.class);

    /** The launch products, in the order the dashboard lists them (app_spec section 4). */
    static final List<String> PRODUCT_CODES = List.of("TERM_LIFE", "MOTOR", "HOUSEHOLD");
    static final String TOTAL = "total";
    /** The most policy numbers in one IN list, so a large portfolio never builds an unbounded query. */
    private static final int BATCH = 500;
    /** The fixed message of every refused {@code asOf} (AC-11.1, AC-11.6); it never echoes the value. */
    private static final String AS_OF_RULE = "must be a date in the format YYYY-MM-DD from "
            + PortfolioRules.EARLIEST_AS_OF + " to " + PortfolioRules.LATEST_AS_OF;

    private final PolicyRepository policies;
    private final PolicyTermRepository terms;
    private final EndorsementRepository endorsements;
    private final RefundRecordRepository refunds;
    private final PortfolioRules portfolioRules;
    private final RenewalPolicy renewalPolicy;
    private final Clock clock;

    public DashboardService(PolicyRepository policies, PolicyTermRepository terms, EndorsementRepository endorsements,
            RefundRecordRepository refunds, PortfolioRules portfolioRules, RenewalPolicy renewalPolicy, Clock clock) {
        this.policies = policies;
        this.terms = terms;
        this.endorsements = endorsements;
        this.refunds = refunds;
        this.portfolioRules = portfolioRules;
        this.renewalPolicy = renewalPolicy;
        this.clock = clock;
    }

    /**
     * The dashboard as of a date.
     *
     * @param asOfText the raw {@code asOf} query parameter, or null for today
     * @throws ValidationFailedException (400) naming {@code asOf} when it is not a date in the format YYYY-MM-DD, or
     *                                   is outside 2000-01-01..2100-12-31 (AC-11.6)
     */
    @Transactional(readOnly = true)
    public PortfolioDashboard dashboard(String asOfText) {
        LocalDate asOf = resolveAsOf(asOfText);
        List<PolicyEntity> everyPolicy = policies.findAllByOrderByPolicyNumberAsc();
        List<String> numbers = everyPolicy.stream().map(PolicyEntity::getPolicyNumber).toList();
        Map<String, List<PolicyTermEntity>> termsByPolicy = new LinkedHashMap<>();
        Map<String, List<BigDecimal>> adjustmentsByPolicy = new LinkedHashMap<>();
        Map<String, List<BigDecimal>> refundsByPolicy = new LinkedHashMap<>();
        for (int from = 0; from < numbers.size(); from += BATCH) {
            List<String> batch = numbers.subList(from, Math.min(numbers.size(), from + BATCH));
            terms.findByPolicyNumberIn(batch).stream()
                    .collect(Collectors.groupingBy(PolicyTermEntity::getPolicyNumber))
                    .forEach(termsByPolicy::put);
            endorsements.findByPolicyNumberIn(batch).stream()
                    .collect(Collectors.groupingBy(EndorsementEntity::getPolicyNumber,
                            Collectors.mapping(EndorsementEntity::getAdjustment, Collectors.toList())))
                    .forEach(adjustmentsByPolicy::put);
            refunds.findByPolicyNumberIn(batch).stream()
                    .collect(Collectors.groupingBy(RefundRecordEntity::getPolicyNumber,
                            Collectors.mapping(RefundRecordEntity::getAmount, Collectors.toList())))
                    .forEach(refundsByPolicy::put);
        }

        Map<String, Integer> active = new LinkedHashMap<>();
        Map<String, List<BigDecimal>> paidPremiums = new LinkedHashMap<>();
        Map<String, List<BigDecimal>> adjustments = new LinkedHashMap<>();
        Map<String, List<BigDecimal>> refunded = new LinkedHashMap<>();
        for (String product : PRODUCT_CODES) {
            active.put(product, 0);
            paidPremiums.put(product, new ArrayList<>());
            adjustments.put(product, new ArrayList<>());
            refunded.put(product, new ArrayList<>());
        }
        List<PipelineEntry> pipeline = new ArrayList<>();
        List<String> forecast = new ArrayList<>();

        for (PolicyEntity policy : everyPolicy) {
            String product = policy.getProductCode();
            if (!PRODUCT_CODES.contains(product)) {
                continue;
            }
            String number = policy.getPolicyNumber();
            List<PolicyTermEntity> policyTerms = termsByPolicy.getOrDefault(number, List.of()).stream()
                    .sorted(Comparator.comparingInt(PolicyTermEntity::getTermNo)).toList();
            if (policy.getStatus().isInForce()) {
                active.merge(product, 1, Integer::sum);
            }
            policyTerms.stream().filter(PolicyTermEntity::isPaid)
                    .forEach(term -> paidPremiums.get(product).add(term.getPremium()));
            adjustments.get(product).addAll(adjustmentsByPolicy.getOrDefault(number, List.of()));
            refunded.get(product).addAll(refundsByPolicy.getOrDefault(number, List.of()));

            List<TermPeriod> periods = policyTerms.stream().map(PolicyTerms::period).toList();
            if (portfolioRules.inRenewalPipeline(policy.getStatus(), periods, asOf)) {
                pipeline.add(pipelineEntry(number, policyTerms, periods, asOf));
            }
            if (portfolioRules.inLapseForecast(policy.getStatus(), periods, asOf)) {
                forecast.add(number);
            }
        }

        Map<String, BigDecimal> collected = new LinkedHashMap<>();
        for (String product : PRODUCT_CODES) {
            collected.put(product, portfolioRules.premiumCollected(paidPremiums.get(product),
                    adjustments.get(product), refunded.get(product)));
        }
        collected.put(TOTAL, portfolioRules.total(List.copyOf(collected.values())));

        pipeline.sort(Comparator.comparing(PipelineEntry::termEnd).thenComparing(PipelineEntry::policyNumber));
        forecast.sort(Comparator.naturalOrder());
        LOG.info("Dashboard read asOf={} policies={} pipeline={} forecast={}", asOf, everyPolicy.size(),
                pipeline.size(), forecast.size());
        return new PortfolioDashboard(asOf, active, collected, pipeline, new LapseForecast(forecast.size(), forecast));
    }

    /** The pipeline entry: the paid term in force on asOf, and the next term's premium and paid flag (if any). */
    private PipelineEntry pipelineEntry(String number, List<PolicyTermEntity> policyTerms, List<TermPeriod> periods,
            LocalDate asOf) {
        TermPeriod inForce = renewalPolicy.termInForce(periods, asOf)
                .orElseThrow(() -> new IllegalStateException("Pipeline policy has no term in force: policyNumber="
                        + number));
        Map<Integer, PolicyTermEntity> byNumber = policyTerms.stream()
                .collect(Collectors.toMap(PolicyTermEntity::getTermNo, Function.identity()));
        Optional<PolicyTermEntity> next = policyTerms.stream().filter(term -> term.getTermNo() > inForce.termNo())
                .findFirst();
        return new PipelineEntry(number, byNumber.get(inForce.termNo()).getEndDate(),
                next.map(PolicyTermEntity::getPremium).orElse(null), next.map(PolicyTermEntity::isPaid).orElse(false));
    }

    private LocalDate resolveAsOf(String asOfText) {
        if (asOfText == null) {
            return LocalDate.now(clock);
        }
        LocalDate asOf;
        try {
            asOf = LocalDate.parse(asOfText);
        } catch (DateTimeParseException unreadable) {
            throw new ValidationFailedException(List.of(new FieldViolation("asOf", AS_OF_RULE)));
        }
        // AC-11.6: a date outside the supported range is the same fixed 400, never a date-arithmetic 500.
        if (!portfolioRules.isSupportedAsOf(asOf)) {
            throw new ValidationFailedException(List.of(new FieldViolation("asOf", AS_OF_RULE)));
        }
        return asOf;
    }
}
