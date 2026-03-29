package com.dkostin.avro_viewer.app.config;

import com.dkostin.avro_viewer.app.domain.model.filter.FilterCriterion;
import com.dkostin.avro_viewer.app.ui.component.FiltersUi;
import com.dkostin.avro_viewer.app.util.DeepSearchEngine;
import org.apache.avro.generic.GenericRecord;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class FilterPredicateFactory {

    /**
     * Compiles filter criteria into a predicate that operates on raw GenericRecord.
     * Used by the paging path (pre-normalization).
     */
    public Predicate<GenericRecord> compile(List<FilterCriterion> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            return _ -> true;
        }

        List<Predicate<GenericRecord>> preds = new java.util.ArrayList<>();
        for (FilterCriterion c : criteria) {
            preds.add(oneRecord(c));
        }

        return rec -> {
            for (var p : preds) {
                if (!p.test(rec)) return false;
            }
            return true;
        };
    }

    /**
     * Compiles filter criteria into a predicate that operates on normalized
     * {@code Map<String, Object>} rows. Supports deep recursive search via
     * {@link DeepSearchEngine} for both [Any Field] and specific field subtrees.
     */
    public Predicate<Map<String, Object>> compileForNormalized(List<FilterCriterion> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            return _ -> true;
        }

        List<Predicate<Map<String, Object>>> preds = new java.util.ArrayList<>();
        for (FilterCriterion c : criteria) {
            preds.add(oneNormalized(c));
        }

        return row -> {
            for (var p : preds) {
                if (!p.test(row)) return false;
            }
            return true;
        };
    }

    // --- GenericRecord predicates (paging path) ---

    private Predicate<GenericRecord> oneRecord(FilterCriterion c) {
        String field = c.field();
        var op = c.op();
        Object raw = c.value();

        return rec -> op.matches(rec.get(field), raw);
    }

    // --- Normalized Map predicates (search path) ---

    private Predicate<Map<String, Object>> oneNormalized(FilterCriterion c) {
        String field = c.field();
        var op = c.op();
        Object raw = c.value();
        String expected = raw != null ? String.valueOf(raw) : null;

        if (FiltersUi.ANY_FIELD.equals(field)) {
            // [Any Field]: deep search the entire row
            return row -> DeepSearchEngine.matches(row, expected, op);
        }

        // Specific field: deep search that field's subtree
        return row -> DeepSearchEngine.matches(row.get(field), expected, op);
    }
}

