package com.horizon.policyforge.domain.ruleset;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The model-specific {@code rating} section of a rule set, held generically so adding a product is a data change.
 * Every decimal is a {@link BigDecimal}.
 *
 * @param scalars single factors such as {@code baseRate} or {@code policyFee}
 * @param bands   banded tables such as {@code vehicleAgeFactor}, in file order
 * @param lookups keyed tables such as {@code useFactor}, keyed by the string used in the rule file
 */
public record RatingFactors(
        Map<String, BigDecimal> scalars,
        Map<String, List<Band>> bands,
        Map<String, Map<String, BigDecimal>> lookups) {

    public RatingFactors {
        scalars = Collections.unmodifiableMap(new LinkedHashMap<>(scalars));
        bands = copyBands(bands);
        lookups = copyLookups(lookups);
    }

    private static Map<String, List<Band>> copyBands(Map<String, List<Band>> source) {
        Map<String, List<Band>> copy = new LinkedHashMap<>();
        source.forEach((name, rows) -> copy.put(name, List.copyOf(rows)));
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Map<String, BigDecimal>> copyLookups(Map<String, Map<String, BigDecimal>> source) {
        Map<String, Map<String, BigDecimal>> copy = new LinkedHashMap<>();
        source.forEach((name, entries) ->
                copy.put(name, Collections.unmodifiableMap(new LinkedHashMap<>(entries))));
        return Collections.unmodifiableMap(copy);
    }

    /** The scalar factor {@code name}; throws when absent (a validated rule set always has the required ones). */
    public BigDecimal scalar(String name) {
        BigDecimal value = scalars.get(name);
        if (value == null) {
            throw new IllegalStateException("Rating factor not found: " + name);
        }
        return value;
    }

    /** The band table {@code name}; throws when absent. */
    public List<Band> bandTable(String name) {
        List<Band> table = bands.get(name);
        if (table == null) {
            throw new IllegalStateException("Rating band table not found: " + name);
        }
        return table;
    }

    /** The keyed table {@code name}; throws when absent. */
    public Map<String, BigDecimal> lookup(String name) {
        Map<String, BigDecimal> table = lookups.get(name);
        if (table == null) {
            throw new IllegalStateException("Rating lookup table not found: " + name);
        }
        return table;
    }
}
