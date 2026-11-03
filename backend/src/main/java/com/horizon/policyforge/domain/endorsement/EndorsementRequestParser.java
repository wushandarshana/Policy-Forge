package com.horizon.policyforge.domain.endorsement;

import java.util.List;
import java.util.Map;

/**
 * Reads the shape of an endorsement request (plan P-5, step 1) from the raw JSON object the web layer received.
 * <b>Stub for the red commit:</b> it accepts nothing and reports nothing, so the shape tests fail on their assertions.
 */
public final class EndorsementRequestParser {

    /** One shape problem: the dotted request field and a fixed message that never contains the submitted value. */
    public record ShapeViolation(String field, String message) {
    }

    /** The outcome of {@link #parse}: a typed request, or every shape problem. */
    public record Parsed(EndorsementRequest request, List<ShapeViolation> violations) {

        public Parsed {
            violations = List.copyOf(violations);
        }

        public boolean isValid() {
            return violations.isEmpty();
        }
    }

    public Parsed parse(Map<String, ?> body) {
        return new Parsed(null, List.of());
    }

    public List<ShapeViolation> checkProduct(String productCode, EndorsementRequest request) {
        return List.of();
    }
}
