package com.horizon.archfixtures.reprice.other;

import java.math.BigDecimal;

/** Fixture: calls another owner's {@code repriceOffer} (allowed: the rule protects only the named owner). */
public class OtherCaller {

    public void run() {
        new OtherPricer().repriceOffer(new BigDecimal("2.00"));
    }
}
