package com.horizon.policyforge.domain.rating;

import com.horizon.policyforge.domain.ruleset.Band;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Lookups shared by the rating models. They read factors from the rule set and never supply a default. */
final class RatingSupport {

    private RatingSupport() {
    }

    /** The factor of the band that contains {@code value}, inclusive at both ends. */
    static BigDecimal bandFactor(List<Band> bands, int value, String table) {
        return bands.stream().filter(band -> band.contains(value)).map(Band::factor).findFirst()
                .orElseThrow(() -> new IllegalStateException("Value outside every band of " + table));
    }

    /** The factor stored under {@code key} in a keyed table. */
    static BigDecimal keyed(Map<String, BigDecimal> table, String key, String name) {
        BigDecimal factor = table.get(key);
        if (factor == null) {
            throw new IllegalStateException("Rating table " + name + " has no entry for the requested key");
        }
        return factor;
    }
}
