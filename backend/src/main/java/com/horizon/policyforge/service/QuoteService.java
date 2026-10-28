package com.horizon.policyforge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizon.policyforge.domain.rating.InvalidRiskInputException;
import com.horizon.policyforge.domain.rating.PremiumBreakdown;
import com.horizon.policyforge.domain.rating.PremiumCalculator;
import com.horizon.policyforge.domain.rating.PremiumFactor;
import com.horizon.policyforge.domain.rating.RiskInputSchema;
import com.horizon.policyforge.domain.ruleset.RuleSetVersion;
import com.horizon.policyforge.repository.QuoteEntity;
import com.horizon.policyforge.repository.QuoteRepository;
import com.horizon.policyforge.service.ValidationFailedException.FieldViolation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The quote use cases (AC-01): price a risk against the product's active rule version, store the quote immutably, and
 * read it back for the public API. The premium comes from the domain {@link PremiumCalculator}. This class loads the
 * rule set, delegates, stores, and never logs risk inputs (NFR-03): it logs the quote ID only. Undeclared risk-input
 * keys are rejected by the rating model (AC-01.9), so only declared inputs are ever stored.
 */
@Service
public class QuoteService {

    private static final Logger LOG = LoggerFactory.getLogger(QuoteService.class);

    private static final TypeReference<List<StoredFactor>> FACTORS = new TypeReference<>() {
    };
    private static final TypeReference<LinkedHashMap<String, Object>> INPUTS = new TypeReference<>() {
    };

    /** A breakdown entry as stored: the value is a decimal string so no scale is lost. */
    private record StoredFactor(String factor, String value) {
    }

    private final RuleSetService ruleSets;
    private final PremiumCalculator calculator;
    private final QuoteRepository quotes;
    private final ObjectMapper mapper;
    private final Clock clock;

    public QuoteService(RuleSetService ruleSets, PremiumCalculator calculator, QuoteRepository quotes,
            ObjectMapper mapper, Clock clock) {
        this.ruleSets = ruleSets;
        this.calculator = calculator;
        this.quotes = quotes;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Prices and stores a quote.
     *
     * @throws ValidationFailedException when the request is incomplete or any risk input is missing or invalid
     * @throws NoActiveRuleSetException  (404) when the product is unknown or has no version in force today
     */
    @Transactional
    public QuoteResult create(String productCode, Map<String, Object> riskInputs) {
        List<FieldViolation> shape = new ArrayList<>();
        if (productCode == null || productCode.isBlank()) {
            shape.add(new FieldViolation("productCode", "is required"));
        }
        if (riskInputs == null) {
            shape.add(new FieldViolation("riskInputs", "is required"));
        }
        if (!shape.isEmpty()) {
            throw new ValidationFailedException(shape);
        }

        RuleSetVersion rules = ruleSets.activeVersion(productCode);
        PremiumBreakdown priced;
        try {
            priced = calculator.calculate(rules, riskInputs);
        } catch (InvalidRiskInputException e) {
            throw new ValidationFailedException(e.violations().stream()
                    .map(v -> new FieldViolation(v.field(), v.message()))
                    .toList());
        }

        LocalDate today = LocalDate.now(clock);
        QuoteEntity saved = quotes.save(QuoteEntity.of(
                "Q-" + UUID.randomUUID(),
                rules.productCode(),
                rules.version(),
                priced.sumInsured(),
                priced.annualPremium(),
                write(priced.factors().stream()
                        .map(f -> new StoredFactor(f.factor(), f.value().toPlainString()))
                        .toList()),
                write(riskInputs),
                today.plusDays(rules.lifecycle().quoteValidityDays()),
                Instant.now(clock)));
        LOG.info("Quote created quoteId={} productCode={} ruleVersion={}", saved.getQuoteId(), saved.getProductCode(),
                saved.getRuleVersion());
        return toResult(saved);
    }

    /** The public view of a stored quote. 404 when there is no such quote. */
    @Transactional(readOnly = true)
    public QuoteResult get(String quoteId) {
        return quotes.findByQuoteId(quoteId)
                .map(this::toResult)
                .orElseThrow(() -> new ResourceNotFoundException("Quote not found"));
    }

    private QuoteResult toResult(QuoteEntity entity) {
        List<PremiumFactor> breakdown = read(entity.getBreakdownJson(), FACTORS).stream()
                .map(f -> new PremiumFactor(f.factor(), new BigDecimal(f.value())))
                .toList();
        // Allow-list (NFR-04.2): only the rating model's declared, non-health inputs; nothing else is ever copied.
        RiskInputSchema schema = RiskInputSchema.forModel(
                ruleSets.version(entity.getProductCode(), entity.getRuleVersion()).model());
        Map<String, Object> publicInputs = schema.publicView(read(entity.getRiskInputsJson(), INPUTS));
        return new QuoteResult(entity.getQuoteId(), entity.getProductCode(), entity.getRuleVersion(),
                entity.getSumInsured(), entity.getAnnualPremium(), breakdown, entity.getValidUntil(), publicInputs);
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise quote data", e);
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored quote data cannot be read", e);
        }
    }
}
