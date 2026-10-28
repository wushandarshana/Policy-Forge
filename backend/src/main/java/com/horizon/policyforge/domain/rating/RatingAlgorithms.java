package com.horizon.policyforge.domain.rating;

import com.horizon.policyforge.domain.ruleset.RatingModel;

/** Selects the rating algorithm that a rule set's {@code ratingModel} names. */
final class RatingAlgorithms {

    private static final RatingAlgorithm TERM_LIFE = new TermLifeRatingModel();
    private static final RatingAlgorithm MOTOR = new MotorRatingModel();
    private static final RatingAlgorithm HOUSEHOLD = new HouseholdRatingModel();

    private RatingAlgorithms() {
    }

    static RatingAlgorithm forModel(RatingModel model) {
        return switch (model) {
            case TERM_LIFE -> TERM_LIFE;
            case MOTOR -> MOTOR;
            case HOUSEHOLD -> HOUSEHOLD;
        };
    }
}
