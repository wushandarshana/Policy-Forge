package com.horizon.archfixtures.reprice.service;

import com.horizon.archfixtures.reprice.repository.OfferTerm;
import java.math.BigDecimal;

/** Fixture: the one class that may call {@code repriceOffer}, standing in for EndorsementService (not rejected). */
public class FixtureEndorsementService {

    public void endorse() {
        new OfferTerm().repriceOffer(new BigDecimal("9490.00"));
    }
}
