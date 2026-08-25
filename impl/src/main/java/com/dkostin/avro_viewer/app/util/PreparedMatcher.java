package com.dkostin.avro_viewer.app.util;

import com.dkostin.avro_viewer.app.domain.model.filter.MatchOperation;

import java.math.BigDecimal;
import java.util.List;

public final class PreparedMatcher {
    private final MatchOperation op;
    private final String expectedStr;
    private final BigDecimal expectedBigDecimal;
    private final List<String> inValues;
    private final List<BigDecimal> inDecimals;

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
        if (op == MatchOperation.IN && expectedStr != null) {
            this.inValues = splitInValues(expectedStr);
            this.inDecimals = inValues.stream()
                    .map(s -> { try { return new BigDecimal(s); } catch (NumberFormatException e) { return null; } })
                    .filter(java.util.Objects::nonNull)
                    .toList();
        } else {
            this.inValues = List.of();
            this.inDecimals = List.of();
        }
    }

    public boolean isSizeOperation() {
        return op == MatchOperation.SIZE_EQUALS 
            || op == MatchOperation.SIZE_GREATER_THAN 
            || op == MatchOperation.SIZE_LESS_THAN;
    }

    public boolean matches(Object actual) {
        if (op == MatchOperation.IS_NULL) return actual == null;
        if (op == MatchOperation.NOT_NULL) return actual != null;
        if (op == MatchOperation.SIZE_EQUALS || op == MatchOperation.SIZE_GREATER_THAN || op == MatchOperation.SIZE_LESS_THAN) {
            return op.matches(actual, expectedStr);
        }
        if (actual == null) return false;

        if (op == MatchOperation.IN) {
            if (actual instanceof Number n && !inDecimals.isEmpty()) {
                BigDecimal bd = toBigDecimal(n);
                return inDecimals.stream().anyMatch(d -> d.compareTo(bd) == 0);
            }
            String normalized = normalize(actual);
            return inValues.contains(normalized);
        }

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

    private static List<String> splitInValues(String input) {
        List<String> result = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\\' && i + 1 < input.length() && input.charAt(i + 1) == ',') {
                current.append(',');
                i++; // skip the escaped comma
            } else if (c == ',') {
                String val = current.toString().trim();
                if (!val.isEmpty()) result.add(val);
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        String last = current.toString().trim();
        if (!last.isEmpty()) result.add(last);
        return List.copyOf(result);
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
