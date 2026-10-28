package com.horizon.policyforge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.domain.ruleset.RuleSetVersion;
import java.time.Instant;

/**
 * A stored rule-set version as the catalog returns it.
 *
 * @param ruleSet     the parsed domain model
 * @param document    the stored JSON, for read-only display
 * @param publishedAt when the row was stored
 * @param active      whether this is the version in force today
 */
public record PublishedRuleSet(RuleSetVersion ruleSet, JsonNode document, Instant publishedAt, boolean active) {
}
