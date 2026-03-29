package com.dkostin.avro_viewer.app.domain.model.filter;

import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@RequiredArgsConstructor
public enum MatchOperation {
    EQUALS("equals") {
        @Override
        public boolean matches(Object actual, Object expected) {
            // Numeric comparison: if actual is a Number, try mathematical equality first
            if (actual instanceof Number actualNum && expected != null) {
                try {
                    return toBigDecimal(actualNum).compareTo(new BigDecimal(normalize(expected))) == 0;
                } catch (NumberFormatException ignored) {
                    // expected is not a valid number — fall through to string comparison
                }
            }
            return normalize(actual).equals(normalize(expected));
        }
    },
    CONTAINS("contains") {
        @Override
        public boolean matches(Object actual, Object expected) {
            return normalize(actual).contains(normalize(expected));
        }
    },
    STARTS_WITH("starts with") {
        @Override
        public boolean matches(Object actual, Object expected) {
            return normalize(actual).startsWith(normalize(expected));
        }
    },
    ENDS_WITH("ends with") {
        @Override
        public boolean matches(Object actual, Object expected) {
            return normalize(actual).endsWith(normalize(expected));
        }
    },
    IS_NULL("is null") {
        @Override
        public boolean matches(Object actual, Object expected) {
            return actual == null;
        }
    },
    NOT_NULL("not null") {
        @Override
        public boolean matches(Object actual, Object expected) {
            return actual != null;
        }
    };

    private final String label;

    /**
     * Tests whether the actual field value satisfies this operation against the expected value.
     */
    public abstract boolean matches(Object actual, Object expected);

    @Override
    public String toString() {
        return label;
    }

    /**
     * Normalizes any value to a plain String for lexical comparison.
     * Handles BigDecimal (strips trailing zeros), CharSequence (Avro Utf8), and Enum.
     */
    private static String normalize(Object value) {
        if (value == null) return "";
        if (value instanceof BigDecimal bd) return bd.stripTrailingZeros().toPlainString();
        if (value instanceof CharSequence cs) return cs.toString();
        if (value instanceof Enum<?> e) return e.name();
        return String.valueOf(value);
    }

    /**
     * Converts any Number to BigDecimal for precise mathematical comparison.
     */
    private static BigDecimal toBigDecimal(Number n) {
        if (n instanceof BigDecimal bd) return bd;
        if (n instanceof Long l) return BigDecimal.valueOf(l);
        if (n instanceof Integer i) return BigDecimal.valueOf(i);
        if (n instanceof Double d) return BigDecimal.valueOf(d);
        if (n instanceof Float f) return BigDecimal.valueOf(f.doubleValue());
        return new BigDecimal(n.toString());
    }
}



