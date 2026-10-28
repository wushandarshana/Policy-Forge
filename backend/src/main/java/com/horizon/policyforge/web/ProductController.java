package com.horizon.policyforge.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.service.ProductSummary;
import com.horizon.policyforge.service.PublishedRuleSet;
import com.horizon.policyforge.service.RuleSetService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Product catalog endpoints (AC-02): the public reads and the PRODUCT_MANAGER publish. Maps requests to
 * {@link RuleSetService} and results to DTOs. No business logic lives here.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    /** Contract C1: one catalog entry. */
    public record ProductResponse(String productCode, String productName, String ratingModel, int activeVersion) {
        static ProductResponse from(ProductSummary summary) {
            return new ProductResponse(summary.productCode(), summary.productName(), summary.ratingModel(),
                    summary.activeVersion());
        }
    }

    /** A stored version with its rule file, for read-only display in the Catalog Manager. */
    public record RuleSetVersionResponse(
            String productCode,
            String productName,
            int version,
            String ratingModel,
            LocalDate effectiveFrom,
            boolean active,
            Instant publishedAt,
            JsonNode ruleSet) {
        static RuleSetVersionResponse from(PublishedRuleSet published) {
            return new RuleSetVersionResponse(
                    published.ruleSet().productCode(),
                    published.ruleSet().productName(),
                    published.ruleSet().version(),
                    published.ruleSet().ratingModel(),
                    published.ruleSet().effectiveFrom(),
                    published.active(),
                    published.publishedAt(),
                    published.document());
        }
    }

    private final RuleSetService ruleSets;

    public ProductController(RuleSetService ruleSets) {
        this.ruleSets = ruleSets;
    }

    /** Public. Every product with a version in force today. */
    @GetMapping
    public List<ProductResponse> products() {
        return ruleSets.listProducts().stream().map(ProductResponse::from).toList();
    }

    /** Public. All stored versions of a product. */
    @GetMapping("/{code}/versions")
    public List<RuleSetVersionResponse> versions(@PathVariable("code") String code) {
        return ruleSets.listVersions(code).stream().map(RuleSetVersionResponse::from).toList();
    }

    /**
     * PRODUCT_MANAGER only. The body is the rule-set JSON; the server assigns the version number. JSON only (NFR-04.3):
     * a cross-site {@code text/plain} form POST is a 415, so cached Basic credentials cannot publish.
     */
    @PostMapping(value = "/{code}/versions", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('PRODUCT_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public RuleSetVersionResponse publish(@PathVariable("code") String code, @RequestBody String body,
            Authentication authentication) {
        return RuleSetVersionResponse.from(ruleSets.publish(authentication.getName(), code, body));
    }
}
