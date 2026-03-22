package com.dkostin.avro_viewer.app.domain.model.filter;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum MatchOperation {
    EQUALS("equals") {
        @Override
        public boolean matches(Object actual, Object expected) {
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
     * Normalizes Avro types (Utf8, CharSequence, Enum) to a plain String for comparison.
     */
    private static String normalize(Object value) {
        if (value == null) return "";
        if (value instanceof CharSequence cs) return cs.toString();
        if (value instanceof Enum<?> e) return e.name();
        return String.valueOf(value);
    }
}

