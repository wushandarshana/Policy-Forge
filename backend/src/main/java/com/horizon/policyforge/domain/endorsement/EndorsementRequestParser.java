package com.horizon.policyforge.domain.endorsement;

import com.horizon.policyforge.domain.EchoedKeySanitiser;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Reads the shape of an endorsement request (plan P-5, step 1) from the raw JSON object the web layer received, and
 * turns it into a typed {@link EndorsementRequest}. Every shape problem is collected, each naming its dotted request
 * field with a fixed message that never contains the submitted value. A shape problem is one of: an unknown key, a
 * missing or unknown {@code type}, a missing payload key, a wrong JSON type, a malformed date or decimal, a share that is
 * not a whole number, or a blank or over-long name, line or city.
 *
 * <p>The business rules are not shape rules and are left to {@link EndorsementValidator}: the postcode format
 * ({@code POSTCODE_INVALID}), the relationship value ({@code NOMINEE_RELATIONSHIP_INVALID}), the share total, the
 * nominee count and the sum-insured limits. A sum-insured key that belongs to another product can only be recognised
 * once the policy is known, so {@link #checkProduct} does that second part of the shape check.
 */
public final class EndorsementRequestParser {

    /** One shape problem: the dotted request field and a fixed message that never contains the submitted value. */
    public record ShapeViolation(String field, String message) {
    }

    /** The outcome of {@link #parse}: a typed request (no violations), or every shape problem (no request). */
    public record Parsed(EndorsementRequest request, List<ShapeViolation> violations) {

        public Parsed {
            violations = List.copyOf(violations);
        }

        public boolean isValid() {
            return violations.isEmpty();
        }
    }

    static final String UNKNOWN_KEY = "is not an accepted field";
    static final String REQUIRED = "is required";
    static final String NOT_TEXT = "must be text";
    static final String NOT_AN_OBJECT = "must be an object";
    static final String BLANK = "must not be blank";
    static final String NOT_A_DATE = "must be a date in the format YYYY-MM-DD";
    static final String NOT_A_DECIMAL = "must be a decimal amount with at most 2 decimal places";
    static final String NOT_A_WHOLE_NUMBER = "must be a whole number";

    private static final int MAX_LINE1 = 200;
    private static final int MAX_CITY = 100;
    private static final int MAX_NAME = 100;

    private static final Pattern DECIMAL = Pattern.compile("-?[0-9]{1,15}(\\.[0-9]{1,2})?");

    private static final String TYPE = "type";
    private static final String EFFECTIVE_DATE = "effectiveDate";
    private static final String ADDRESS = "address";
    private static final String NOMINEE = "nominee";
    private static final String NEW_SUM_INSURED = "newSumInsured";
    private static final String NEW_BUILDING = "newBuildingSumInsured";
    private static final String NEW_CONTENTS = "newContentsSumInsured";

    private static final Set<String> ADDRESS_KEYS = Set.of("line1", "city", "postcode");
    private static final Set<String> NOMINEE_KEYS = Set.of("name", "relationship", "sharePercent");

    /**
     * Reads {@code body}, the endorsement request as the JSON reader produced it (an object of strings, whole numbers,
     * decimals and nested objects).
     */
    public Parsed parse(Map<String, ?> body) {
        Map<String, ?> raw = body == null ? Map.of() : body;
        List<ShapeViolation> violations = new ArrayList<>();
        EndorsementType type = readType(raw.get(TYPE), violations);
        LocalDate effectiveDate = readDate(raw.get(EFFECTIVE_DATE), violations);
        if (type == null) {
            return new Parsed(null, violations);
        }
        rejectUnknownKeys(raw.keySet(), allowedKeys(type), "", violations);

        EndorsementRequest request = switch (type) {
            case CHANGE_ADDRESS -> {
                Address address = readAddress(raw.get(ADDRESS), violations);
                yield new EndorsementRequest(type, effectiveDate, address, null, null, null, null);
            }
            case ADD_NOMINEE -> {
                Nominee nominee = readNominee(raw.get(NOMINEE), violations);
                yield new EndorsementRequest(type, effectiveDate, null, nominee, null, null, null);
            }
            case CHANGE_SUM_INSURED -> readSumInsured(raw, effectiveDate, violations);
        };
        return violations.isEmpty() ? new Parsed(request, List.of()) : new Parsed(null, violations);
    }

    /**
     * The product-specific part of the sum-insured shape: TERM_LIFE and MOTOR take {@code newSumInsured} only, and
     * HOUSEHOLD takes the building and contents keys only, with at least one of them. Other types have nothing to check.
     */
    public List<ShapeViolation> checkProduct(String productCode, EndorsementRequest request) {
        List<ShapeViolation> violations = new ArrayList<>();
        if (request.type() != EndorsementType.CHANGE_SUM_INSURED) {
            return violations;
        }
        if ("HOUSEHOLD".equals(productCode)) {
            if (request.newSumInsured() != null) {
                violations.add(new ShapeViolation(NEW_SUM_INSURED, UNKNOWN_KEY));
            }
            if (request.newBuildingSumInsured() == null && request.newContentsSumInsured() == null) {
                violations.add(new ShapeViolation(NEW_BUILDING,
                        "at least one of newBuildingSumInsured and newContentsSumInsured is required"));
            }
        } else {
            if (request.newBuildingSumInsured() != null) {
                violations.add(new ShapeViolation(NEW_BUILDING, UNKNOWN_KEY));
            }
            if (request.newContentsSumInsured() != null) {
                violations.add(new ShapeViolation(NEW_CONTENTS, UNKNOWN_KEY));
            }
            if (request.newSumInsured() == null) {
                violations.add(new ShapeViolation(NEW_SUM_INSURED, REQUIRED));
            }
        }
        return violations;
    }

    // ---- type and effective date ----

    private static EndorsementType readType(Object value, List<ShapeViolation> out) {
        if (value == null) {
            out.add(new ShapeViolation(TYPE, REQUIRED));
            return null;
        }
        if (value instanceof String text) {
            for (EndorsementType type : EndorsementType.values()) {
                if (type.name().equals(text)) {
                    return type;
                }
            }
        }
        out.add(new ShapeViolation(TYPE, "must be one of CHANGE_ADDRESS, ADD_NOMINEE, CHANGE_SUM_INSURED"));
        return null;
    }

    private static LocalDate readDate(Object value, List<ShapeViolation> out) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            try {
                return LocalDate.parse(text);
            } catch (DateTimeParseException unreadable) {
                // reported below
            }
        }
        out.add(new ShapeViolation(EFFECTIVE_DATE, NOT_A_DATE));
        return null;
    }

    private static Set<String> allowedKeys(EndorsementType type) {
        return switch (type) {
            case CHANGE_ADDRESS -> Set.of(TYPE, EFFECTIVE_DATE, ADDRESS);
            case ADD_NOMINEE -> Set.of(TYPE, EFFECTIVE_DATE, NOMINEE);
            case CHANGE_SUM_INSURED -> Set.of(TYPE, EFFECTIVE_DATE, NEW_SUM_INSURED, NEW_BUILDING, NEW_CONTENTS);
        };
    }

    /**
     * One violation per unknown key, named with its fixed prefix and the key in its shared sanitised form (P-14): at
     * most 64 characters, with anything outside {@code A-Z a-z 0-9 _ . -} replaced by an underscore. Keys that sanitise
     * alike are named once.
     */
    private static void rejectUnknownKeys(Set<String> keys, Set<String> allowed, String prefix,
            List<ShapeViolation> out) {
        Set<String> named = new LinkedHashSet<>();
        for (String key : new TreeSet<>(keys)) {
            if (!allowed.contains(key) && named.add(EchoedKeySanitiser.sanitise(key))) {
                out.add(new ShapeViolation(prefix + EchoedKeySanitiser.sanitise(key), UNKNOWN_KEY));
            }
        }
    }

    // ---- address ----

    private static Address readAddress(Object value, List<ShapeViolation> out) {
        Map<?, ?> map = readObject(value, ADDRESS, out);
        if (map == null) {
            return null;
        }
        rejectUnknownKeys(keysOf(map), ADDRESS_KEYS, "address.", out);
        String line1 = readText(map.get("line1"), "address.line1", MAX_LINE1, true, out);
        String city = readText(map.get("city"), "address.city", MAX_CITY, true, out);
        // The postcode only has to be text here: whether it is 6 digits is a business rule (POSTCODE_INVALID).
        String postcode = readText(map.get("postcode"), "address.postcode", Integer.MAX_VALUE, false, out);
        return new Address(line1, city, postcode);
    }

    // ---- nominee ----

    private static Nominee readNominee(Object value, List<ShapeViolation> out) {
        Map<?, ?> map = readObject(value, NOMINEE, out);
        if (map == null) {
            return null;
        }
        rejectUnknownKeys(keysOf(map), NOMINEE_KEYS, "nominee.", out);
        String name = readText(map.get("name"), "nominee.name", MAX_NAME, true, out);
        // The relationship only has to be text here: whether it is one of the five values is NOMINEE_RELATIONSHIP_INVALID.
        String relationship = readText(map.get("relationship"), "nominee.relationship", Integer.MAX_VALUE, false, out);
        Integer share = readShare(map.get("sharePercent"), out);
        return new Nominee(name, relationship, share == null ? 0 : share);
    }

    private static Integer readShare(Object value, List<ShapeViolation> out) {
        String field = "nominee.sharePercent";
        if (value == null) {
            out.add(new ShapeViolation(field, REQUIRED));
            return null;
        }
        boolean wholeNumber = value instanceof Integer || value instanceof Long || value instanceof Short
                || value instanceof Byte || value instanceof BigInteger;
        if (!wholeNumber) {
            out.add(new ShapeViolation(field, NOT_A_WHOLE_NUMBER));
            return null;
        }
        BigInteger number = value instanceof BigInteger big ? big : BigInteger.valueOf(((Number) value).longValue());
        if (number.signum() < 1) {
            out.add(new ShapeViolation(field, "must be at least 1"));
            return null;
        }
        if (number.bitLength() > 31) {
            out.add(new ShapeViolation(field, "is too large"));
            return null;
        }
        return number.intValue();
    }

    // ---- sum insured ----

    private static EndorsementRequest readSumInsured(Map<String, ?> raw, LocalDate effectiveDate,
            List<ShapeViolation> out) {
        boolean anySupplied = raw.get(NEW_SUM_INSURED) != null || raw.get(NEW_BUILDING) != null
                || raw.get(NEW_CONTENTS) != null;
        if (!anySupplied) {
            out.add(new ShapeViolation(NEW_SUM_INSURED, REQUIRED));
        }
        BigDecimal single = readDecimal(raw.get(NEW_SUM_INSURED), NEW_SUM_INSURED, out);
        BigDecimal building = readDecimal(raw.get(NEW_BUILDING), NEW_BUILDING, out);
        BigDecimal contents = readDecimal(raw.get(NEW_CONTENTS), NEW_CONTENTS, out);
        return new EndorsementRequest(EndorsementType.CHANGE_SUM_INSURED, effectiveDate, null, null, single, building,
                contents);
    }

    /** An absent or null value is no value; anything else must be a decimal string of up to 2 dp. */
    private static BigDecimal readDecimal(Object value, String field, List<ShapeViolation> out) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text && DECIMAL.matcher(text).matches()) {
            return new BigDecimal(text);
        }
        out.add(new ShapeViolation(field, NOT_A_DECIMAL));
        return null;
    }

    // ---- shared readers ----

    /** The nested object, or null after recording that it is missing or not an object. */
    private static Map<?, ?> readObject(Object value, String field, List<ShapeViolation> out) {
        if (value == null) {
            out.add(new ShapeViolation(field, REQUIRED));
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        out.add(new ShapeViolation(field, NOT_AN_OBJECT));
        return null;
    }

    private static Set<String> keysOf(Map<?, ?> map) {
        Set<String> keys = new TreeSet<>();
        map.keySet().forEach(key -> keys.add(String.valueOf(key)));
        return keys;
    }

    /** Required text; when {@code notBlank} it must contain a visible character and fit in {@code maxLength}. */
    private static String readText(Object value, String field, int maxLength, boolean notBlank,
            List<ShapeViolation> out) {
        if (value == null) {
            out.add(new ShapeViolation(field, REQUIRED));
            return null;
        }
        if (!(value instanceof String text)) {
            out.add(new ShapeViolation(field, NOT_TEXT));
            return null;
        }
        if (notBlank && text.isBlank()) {
            out.add(new ShapeViolation(field, BLANK));
            return null;
        }
        if (text.length() > maxLength) {
            out.add(new ShapeViolation(field, "must be at most " + maxLength + " characters"));
            return null;
        }
        return text;
    }
}
