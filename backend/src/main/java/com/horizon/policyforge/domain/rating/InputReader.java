package com.horizon.policyforge.domain.rating;

import com.horizon.policyforge.domain.Money;
import com.horizon.policyforge.domain.ruleset.Band;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Reads typed risk inputs from the raw request values and records one violation per missing or invalid field.
 * Messages never repeat the submitted value. Numbers are parsed as {@link BigDecimal} from their text, never from a
 * binary floating-point value.
 */
final class InputReader {

    /** Largest magnitude accepted for any numeric input, as a count of integer digits. */
    private static final int MAX_INTEGER_DIGITS = 15;
    private static final Pattern PLAIN_DECIMAL = Pattern.compile("-?\\d{1,15}(\\.\\d{1,10})?");

    private final Map<String, Object> raw;
    private final List<RiskInputViolation> violations = new ArrayList<>();

    InputReader(Map<String, Object> raw) {
        this.raw = raw == null ? Map.of() : raw;
    }

    List<RiskInputViolation> violations() {
        return List.copyOf(violations);
    }

    boolean isValid() {
        return violations.isEmpty();
    }

    /** The submitted inputs, read-only. */
    Map<String, Object> raw() {
        return Collections.unmodifiableMap(raw);
    }

    /** Records each given violation, e.g. one per undeclared input key. */
    void rejectAll(List<RiskInputViolation> found) {
        violations.addAll(found);
    }

    void reject(String field, String message) {
        violations.add(new RiskInputViolation(field, message));
    }

    /** A non-negative amount with at most two decimal places, returned at scale 2. */
    Optional<BigDecimal> money(String field) {
        Optional<BigDecimal> parsed = decimal(field, "must be a decimal amount");
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal value = parsed.get();
        if (value.signum() < 0) {
            reject(field, "must not be negative");
            return Optional.empty();
        }
        if (value.stripTrailingZeros().scale() > Money.FINAL_SCALE) {
            reject(field, "must have at most 2 decimal places");
            return Optional.empty();
        }
        return Optional.of(Money.finalAmount(value));
    }

    /** A decimal greater than zero, kept exactly as given. */
    Optional<BigDecimal> positiveDecimal(String field) {
        Optional<BigDecimal> parsed = decimal(field, "must be a decimal number");
        if (parsed.isPresent() && parsed.get().signum() <= 0) {
            reject(field, "must be greater than zero");
            return Optional.empty();
        }
        return parsed;
    }

    private Optional<BigDecimal> decimal(String field, String invalidMessage) {
        Optional<Object> value = required(field);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        Optional<BigDecimal> parsed = toDecimal(value.get());
        if (parsed.isEmpty()) {
            reject(field, invalidMessage);
        }
        return parsed;
    }

    /** A whole number inside one of the bands, inclusive at both ends. */
    Optional<Integer> wholeInBands(String field, List<Band> bands) {
        int low = bands.stream().mapToInt(Band::min).min().orElse(0);
        int high = bands.stream().mapToInt(Band::max).max().orElse(0);
        Optional<Integer> value = whole(field, "must be a whole number between " + low + " and " + high);
        if (value.isPresent() && bands.stream().noneMatch(band -> band.contains(value.get()))) {
            reject(field, "must be a whole number between " + low + " and " + high);
            return Optional.empty();
        }
        return value;
    }

    /** A whole number of at least {@code minimum}. */
    Optional<Integer> wholeAtLeast(String field, int minimum) {
        Optional<Integer> value = whole(field, "must be a whole number of at least " + minimum);
        if (value.isPresent() && value.get() < minimum) {
            reject(field, "must be a whole number of at least " + minimum);
            return Optional.empty();
        }
        return value;
    }

    /** A string or whole-number value that is one of {@code allowed}, returned in the rule file's key form. */
    Optional<String> oneOf(String field, Collection<String> allowed) {
        Optional<Object> value = required(field);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        String key = null;
        if (value.get() instanceof String text) {
            key = text;
        } else if (value.get() instanceof Number) {
            key = toDecimal(value.get()).filter(d -> d.stripTrailingZeros().scale() <= 0)
                    .map(d -> d.setScale(0, java.math.RoundingMode.UNNECESSARY).toPlainString()).orElse(null);
        }
        if (key == null || !allowed.contains(key)) {
            reject(field, "must be one of: " + String.join(", ", new TreeSet<>(allowed)));
            return Optional.empty();
        }
        return Optional.of(key);
    }

    /** A boolean, or the text {@code true} or {@code false}. */
    Optional<Boolean> bool(String field) {
        Optional<Object> value = required(field);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        if (value.get() instanceof Boolean flag) {
            return Optional.of(flag);
        }
        if ("true".equals(value.get())) {
            return Optional.of(Boolean.TRUE);
        }
        if ("false".equals(value.get())) {
            return Optional.of(Boolean.FALSE);
        }
        reject(field, "must be true or false");
        return Optional.empty();
    }

    /** A list that is present but may be empty. */
    Optional<List<?>> list(String field) {
        Optional<Object> value = required(field);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        if (value.get() instanceof List<?> list) {
            return Optional.of(list);
        }
        reject(field, "must be a list");
        return Optional.empty();
    }

    /**
     * A present (possibly empty) list of at most {@code maxItems} codes, each a string matching {@code code}
     * (AC-01.10). Anything else, including objects, numbers or over-long text in the list, is one violation on
     * {@code field}; the message names the rule, never the submitted value.
     */
    Optional<List<String>> codeList(String field, int maxItems, Pattern code) {
        Optional<List<?>> list = list(field);
        if (list.isEmpty()) {
            return Optional.empty();
        }
        String rule = "must be a list of at most " + maxItems + " codes matching " + code.pattern();
        if (list.get().size() > maxItems) {
            reject(field, rule);
            return Optional.empty();
        }
        List<String> codes = new ArrayList<>();
        for (Object item : list.get()) {
            if (!(item instanceof String text) || !code.matcher(text).matches()) {
                reject(field, rule);
                return Optional.empty();
            }
            codes.add(text);
        }
        return Optional.of(List.copyOf(codes));
    }

    private Optional<Whole> wholeValue(String field) {
        return required(field).map(v -> new Whole(toDecimal(v).filter(d -> d.stripTrailingZeros().scale() <= 0)
                .filter(d -> d.abs().compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) <= 0)
                .map(BigDecimal::intValue)));
    }

    private Optional<Integer> whole(String field, String invalidMessage) {
        Optional<Whole> value = wholeValue(field);
        if (value.isPresent() && value.get().number().isEmpty()) {
            reject(field, invalidMessage);
            return Optional.empty();
        }
        return value.flatMap(Whole::number);
    }

    /** The value of {@code field}, or empty with a "required" violation when it is absent or null. */
    private Optional<Object> required(String field) {
        Object value = raw.get(field);
        if (value == null) {
            if (violations.stream().noneMatch(v -> v.field().equals(field))) {
                reject(field, "is required");
            }
            return Optional.empty();
        }
        return Optional.of(value);
    }

    private record Whole(Optional<Integer> number) {
    }

    /** Parses a number or plain decimal text of limited magnitude; empty for anything else, including booleans. */
    private static Optional<BigDecimal> toDecimal(Object value) {
        try {
            BigDecimal decimal;
            if (value instanceof BigDecimal big) {
                decimal = big;
            } else if (value instanceof BigInteger big) {
                decimal = new BigDecimal(big);
            } else if (value instanceof Number number) {
                decimal = new BigDecimal(number.toString());
            } else if (value instanceof String text && PLAIN_DECIMAL.matcher(text).matches()) {
                decimal = new BigDecimal(text);
            } else {
                return Optional.empty();
            }
            if (decimal.precision() - decimal.scale() > MAX_INTEGER_DIGITS) {
                return Optional.empty();
            }
            return Optional.of(decimal);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
