package com.horizon.policyforge.domain.ruleset;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * Picks the version of a product that is in force: the highest version whose {@code effectiveFrom} is on or before
 * today. The date comes in as an argument (from the injected Clock in the service), so this is pure and repeatable.
 */
public final class ActiveRuleSetResolver {

    public Optional<RuleSetVersion> resolve(Collection<RuleSetVersion> versions, LocalDate today) {
        return versions.stream()
                .filter(version -> version.isEffectiveOn(today))
                .max(Comparator.comparingInt(RuleSetVersion::version));
    }
}
