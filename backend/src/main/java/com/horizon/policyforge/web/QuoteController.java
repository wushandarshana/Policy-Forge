package com.horizon.policyforge.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.horizon.policyforge.service.QuoteResult;
import com.horizon.policyforge.service.QuoteService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public quote endpoints (AC-01, NFR-04.2): create a quote and read one back. Both are listed as public in
 * {@code SecurityConfig}. Maps requests to {@link QuoteService} and results to DTOs. No business logic lives here.
 */
@RestController
@RequestMapping("/api/quotes")
public class QuoteController {

    /** Request body of {@code POST /api/quotes}. Missing parts are reported by the service as 400 with errors[]. */
    public record CreateQuoteRequest(String productCode, Map<String, Object> riskInputs) {
    }

    /** One applied factor. The value is a decimal string (NFR-01). */
    public record BreakdownItem(String factor, String value) {
    }

    /**
     * Contract C2. Money is a decimal string at scale 2. {@code riskInputs} is only present on the read, and never
     * holds health inputs (NFR-04.2).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuoteResponse(
            String quoteId,
            String productCode,
            int ruleVersion,
            String sumInsured,
            String annualPremium,
            List<BreakdownItem> breakdown,
            LocalDate validUntil,
            Map<String, Object> riskInputs) {

        static QuoteResponse created(QuoteResult quote) {
            return from(quote, null);
        }

        static QuoteResponse read(QuoteResult quote) {
            return from(quote, quote.riskInputs());
        }

        private static QuoteResponse from(QuoteResult quote, Map<String, Object> riskInputs) {
            return new QuoteResponse(
                    quote.quoteId(),
                    quote.productCode(),
                    quote.ruleVersion(),
                    quote.sumInsured().toPlainString(),
                    quote.annualPremium().toPlainString(),
                    quote.breakdown().stream()
                            .map(f -> new BreakdownItem(f.factor(), f.value().toPlainString()))
                            .toList(),
                    quote.validUntil(),
                    riskInputs);
        }
    }

    private final QuoteService quotes;

    public QuoteController(QuoteService quotes) {
        this.quotes = quotes;
    }

    /** JSON only (NFR-04.3). Bodies over 16 KB are refused before this point with 413 (AC-01.9). */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public QuoteResponse create(@RequestBody CreateQuoteRequest request) {
        return QuoteResponse.created(quotes.create(request.productCode(), request.riskInputs()));
    }

    @GetMapping("/{quoteId}")
    public QuoteResponse read(@PathVariable String quoteId) {
        return QuoteResponse.read(quotes.get(quoteId));
    }
}
