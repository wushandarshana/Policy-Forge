package com.horizon.archfixtures.reprice.web;

import com.horizon.archfixtures.reprice.repository.OfferTerm;
import java.math.BigDecimal;

/** Fixture: a class other than the fixture's endorsement service that re-prices an offer (rejected by the rule). */
public class RepriceCaller {

    public void handle() {
        new OfferTerm().repriceOffer(new BigDecimal("1.00"));
    }
}
