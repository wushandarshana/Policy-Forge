package com.horizon.policyforge.service;

import com.horizon.policyforge.domain.ruleset.ActiveRuleSetResolver;
import com.horizon.policyforge.domain.ruleset.RuleSetValidator;
import com.horizon.policyforge.domain.ruleset.RuleSetVersion;
import com.horizon.policyforge.domain.ruleset.RuleSetViolation;
import com.horizon.policyforge.repository.RuleSetVersionEntity;
import com.horizon.policyforge.repository.RuleSetVersionRepository;
import com.horizon.policyforge.service.RuleSetJsonParser.ParsedRuleSet;
import com.horizon.policyforge.service.ValidationFailedException.FieldViolation;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The product catalog use cases (AC-02): resolve the active version of a product, list products and versions, publish
 * a new version, and load the seed files. Business decisions (validation, which version is active) are made in the
 * domain; this class loads, delegates, appends and audits.
 */
@Service
public class RuleSetService {

    private static final Logger LOG = LoggerFactory.getLogger(RuleSetService.class);
    private static final String SYSTEM_ACTOR = "system";

    private final RuleSetVersionRepository versions;
    private final RuleSetJsonParser parser;
    private final RuleSetValidator validator;
    private final ActiveRuleSetResolver resolver;
    private final AuditService audit;
    private final Clock clock;

    public RuleSetService(RuleSetVersionRepository versions, RuleSetJsonParser parser, RuleSetValidator validator,
            ActiveRuleSetResolver resolver, AuditService audit, Clock clock) {
        this.versions = versions;
        this.parser = parser;
        this.validator = validator;
        this.resolver = resolver;
        this.audit = audit;
        this.clock = clock;
    }

    /** The version of {@code productCode} in force today (by the injected Clock). */
    @Transactional(readOnly = true)
    public RuleSetVersion activeVersion(String productCode) {
        return activeVersion(productCode, LocalDate.now(clock));
    }

    /** The version of {@code productCode} in force on {@code asOf}: the highest with effectiveFrom on or before it. */
    @Transactional(readOnly = true)
    public RuleSetVersion activeVersion(String productCode, LocalDate asOf) {
        return resolver.resolve(load(productCode), asOf)
                .orElseThrow(() -> new NoActiveRuleSetException(
                        "No active rule set for product " + productCode + " on " + asOf));
    }

    /** A specific stored version, e.g. the one a quote was priced with. */
    @Transactional(readOnly = true)
    public RuleSetVersion version(String productCode, int version) {
        return versions.findByProductCodeAndVersion(productCode, version)
                .map(this::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No version " + version + " of product " + productCode));
    }

    /** Every product that has a version in force today, in the order the products were first stored. */
    @Transactional(readOnly = true)
    public List<ProductSummary> listProducts() {
        LocalDate today = LocalDate.now(clock);
        Map<String, List<RuleSetVersion>> byProduct = new LinkedHashMap<>();
        for (RuleSetVersionEntity entity : versions.findAllByOrderByIdAsc()) {
            byProduct.computeIfAbsent(entity.getProductCode(), code -> new ArrayList<>()).add(toDomain(entity));
        }
        List<ProductSummary> products = new ArrayList<>();
        byProduct.values().forEach(all -> resolver.resolve(all, today).ifPresent(active ->
                products.add(new ProductSummary(active.productCode(), active.productName(), active.ratingModel(),
                        active.version()))));
        return products;
    }

    /** Every stored version of a product, lowest first. 404 when the product is unknown. */
    @Transactional(readOnly = true)
    public List<PublishedRuleSet> listVersions(String productCode) {
        List<RuleSetVersionEntity> entities = versions.findByProductCodeOrderByVersionAsc(productCode);
        if (entities.isEmpty()) {
            throw new ResourceNotFoundException("No product " + productCode);
        }
        int activeVersion = resolver.resolve(entities.stream().map(this::toDomain).toList(), LocalDate.now(clock))
                .map(RuleSetVersion::version).orElse(-1);
        return entities.stream().map(entity -> published(entity, activeVersion)).toList();
    }

    /**
     * Publishes {@code rawJson} as the next version of {@code productCode} (current max + 1, or 1 for a new product).
     * The body is validated in full first. If anything is wrong, nothing is stored and every violation is reported.
     * On success the row and its audit entry are written in one transaction. Existing versions are never touched.
     */
    @Transactional
    public PublishedRuleSet publish(String actorId, String productCode, String rawJson) {
        int next = versions.findFirstByProductCodeOrderByVersionDesc(productCode)
                .map(latest -> latest.getVersion() + 1)
                .orElse(1);
        String json = parser.withVersion(rawJson, next);
        ParsedRuleSet parsed = parser.parse(json);

        List<RuleSetViolation> problems = new ArrayList<>(parsed.violations());
        if (parsed.readable()) {
            problems.addAll(validator.validate(parsed.ruleSet()));
            String bodyCode = parsed.ruleSet().productCode();
            if (bodyCode != null && !bodyCode.equals(productCode)) {
                problems.add(new RuleSetViolation("productCode", "must match the product in the URL"));
            }
        }
        if (!problems.isEmpty()) {
            throw new ValidationFailedException(problems.stream()
                    .map(problem -> new FieldViolation(problem.path(), problem.message()))
                    .toList());
        }

        RuleSetVersion ruleSet = parsed.ruleSet();
        RuleSetVersionEntity saved = versions.save(
                RuleSetVersionEntity.publish(ruleSet, json, Instant.now(clock), actorId));
        audit.record(actorId, "RULE_SET_PUBLISHED", "RuleSetVersion", productCode + "/v" + ruleSet.version());
        LOG.info("Rule set published productCode={} version={} actorId={}", productCode, ruleSet.version(), actorId);

        int activeVersion = resolver.resolve(load(productCode), LocalDate.now(clock))
                .map(RuleSetVersion::version).orElse(-1);
        return published(saved, activeVersion);
    }

    /**
     * Stores a seed file when (productCode, version) is absent and leaves an existing row alone.
     *
     * @param sourceName where the text came from, for error messages
     * @param slug       the folder name the file was found in, e.g. {@code term-life}
     * @param fileVersion the N of {@code vN.json}
     * @return true when a row was inserted
     * @throws IllegalStateException when the seed file is invalid or does not match its own path
     */
    @Transactional
    public boolean registerSeed(String sourceName, String slug, int fileVersion, String rawJson) {
        ParsedRuleSet parsed = parser.parse(rawJson);
        List<RuleSetViolation> problems = new ArrayList<>(parsed.violations());
        if (parsed.readable()) {
            problems.addAll(validator.validate(parsed.ruleSet()));
        }
        if (problems.isEmpty()) {
            RuleSetVersion ruleSet = parsed.ruleSet();
            if (ruleSet.version() != fileVersion) {
                problems.add(new RuleSetViolation("version", "does not match the file name"));
            }
            if (!slug.equals(ruleSet.productCode().toLowerCase().replace('_', '-'))) {
                problems.add(new RuleSetViolation("productCode", "does not match the folder name"));
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Seed rule set " + sourceName + " is invalid: " + problems);
        }

        RuleSetVersion ruleSet = parsed.ruleSet();
        if (versions.existsByProductCodeAndVersion(ruleSet.productCode(), ruleSet.version())) {
            return false;
        }
        versions.save(RuleSetVersionEntity.publish(ruleSet, rawJson, Instant.now(clock), SYSTEM_ACTOR));
        LOG.info("Rule set seeded productCode={} version={}", ruleSet.productCode(), ruleSet.version());
        return true;
    }

    private List<RuleSetVersion> load(String productCode) {
        return versions.findByProductCodeOrderByVersionAsc(productCode).stream().map(this::toDomain).toList();
    }

    private PublishedRuleSet published(RuleSetVersionEntity entity, int activeVersion) {
        return new PublishedRuleSet(toDomain(entity), parser.document(entity.getRulesJson()), entity.getCreatedAt(),
                entity.getVersion() == activeVersion);
    }

    private RuleSetVersion toDomain(RuleSetVersionEntity entity) {
        ParsedRuleSet parsed = parser.parse(entity.getRulesJson());
        if (!parsed.readable() || !parsed.violations().isEmpty()) {
            throw new IllegalStateException("Stored rule set cannot be read: product=" + entity.getProductCode()
                    + " version=" + entity.getVersion());
        }
        return parsed.ruleSet();
    }
}
