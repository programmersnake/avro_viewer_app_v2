package com.dkostin.avro_viewer.app.filter;

import org.apache.avro.generic.GenericRecord;

import java.util.List;
import java.util.function.Predicate;

public final class FilterPredicateFactory {

    public Predicate<GenericRecord> compile(List<FilterCriterion> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            return _ -> true;
        }

        List<Predicate<GenericRecord>> preds = new java.util.ArrayList<>();
        for (FilterCriterion c : criteria) {
            preds.add(one(c));
        }

        // AND by design
        return rec -> {
            for (var p : preds) {
                if (!p.test(rec)) {
                    return false;
                }
            }
            return true;
        };
    }

    private Predicate<GenericRecord> one(FilterCriterion c) {
        String field = c.field();
        MatchOperation op = c.op();
        Object raw = c.value(); // String or null

        return rec -> {
            Object v = rec.get(field); // Avro may return Utf8, etc.
            return matches(v, op, raw);
        };
    }

    private boolean matches(Object value, MatchOperation op, Object raw) {
        if (op == MatchOperation.IS_NULL) return value == null;
        if (op == MatchOperation.NOT_NULL) return value != null;

        if (raw == null) {
            return value == null;
        }

        String expected = String.valueOf(raw);

        if (value == null) return false;

        // normalize Utf8/CharSequence/Enum
        String actual = normalizeToString(value);

        return switch (op) {
            case EQUALS -> actual.equals(expected);
            case CONTAINS -> actual.contains(expected);
            case STARTS_WITH -> actual.startsWith(expected);
            case ENDS_WITH -> actual.endsWith(expected);
            default -> throw new IllegalStateException("Unsupported op: " + op);
        };
    }

    private String normalizeToString(Object value) {
        if (value instanceof CharSequence cs) {
            return cs.toString();
        }
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        return String.valueOf(value);
    }
}

