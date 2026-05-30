package com.dkostin.avro_viewer.app.util;

import com.dkostin.avro_viewer.app.domain.model.filter.MatchOperation;
import java.math.BigDecimal;

public final class PreparedMatcher {
    private final MatchOperation op;
    private final String expectedStr;
    private final BigDecimal expectedBigDecimal;

    public PreparedMatcher(MatchOperation op, Object expectedRaw) {
        this.op = op;
        this.expectedStr = normalize(expectedRaw);
        BigDecimal parsed = null;
        if (expectedRaw != null) {
            try {
                parsed = new BigDecimal(expectedStr.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        this.expectedBigDecimal = parsed;
    }

    public boolean matches(Object actual) {
        if (op == MatchOperation.IS_NULL) return actual == null;
        if (op == MatchOperation.NOT_NULL) return actual != null;
        if (actual == null) return false;

        return switch (op) {
            case EQUALS -> {
                if (actual instanceof Number actualNum && expectedBigDecimal != null) {
                    yield toBigDecimal(actualNum).compareTo(expectedBigDecimal) == 0;
                }
                yield normalize(actual).equals(expectedStr);
            }
            case CONTAINS -> normalize(actual).contains(expectedStr);
            case STARTS_WITH -> normalize(actual).startsWith(expectedStr);
            case ENDS_WITH -> normalize(actual).endsWith(expectedStr);
            default -> false;
        };
    }

    private static String normalize(Object value) {
        if (value == null) return "";
        if (value instanceof BigDecimal bd) return bd.stripTrailingZeros().toPlainString();
        if (value instanceof CharSequence cs) return cs.toString();
        if (value instanceof Enum<?> e) return e.name();
        return String.valueOf(value);
    }

    private static BigDecimal toBigDecimal(Number n) {
        if (n instanceof BigDecimal bd) return bd;
        if (n instanceof Long l) return BigDecimal.valueOf(l);
        if (n instanceof Integer i) return BigDecimal.valueOf(i);
        if (n instanceof Double d) return BigDecimal.valueOf(d);
        if (n instanceof Float f) return BigDecimal.valueOf(f.doubleValue());
        return new BigDecimal(n.toString());
    }
}
