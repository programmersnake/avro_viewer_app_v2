package com.dkostin.avro_viewer.app.config;

import com.dkostin.avro_viewer.app.domain.model.filter.FilterCriterion;
import com.dkostin.avro_viewer.app.util.DeepSearchEngine;
import org.apache.avro.generic.GenericRecord;

import java.util.List;
import java.util.function.Predicate;

public final class FilterPredicateFactory {

    /**
     * Compiles filter criteria into a predicate on {@link GenericRecord}.
     * <p>
     * All predicates use {@link DeepSearchEngine} for evaluation, which natively
     * traverses Avro types ({@code IndexedRecord}, {@code GenericArray}) without
     * any normalization overhead.
     * <ul>
     *   <li>Wildcard ({@link FilterCriterion#isWildcard()}): passes the entire
     *       {@code GenericRecord} as the DFS root.</li>
     *   <li>Specific field: passes {@code rec.get(field)} as the DFS root,
     *       enabling deep search into nested records, arrays, and maps.</li>
     * </ul>
     */
    public Predicate<GenericRecord> compile(List<FilterCriterion> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            return _ -> true;
        }

        List<Predicate<GenericRecord>> preds = new java.util.ArrayList<>();
        for (FilterCriterion c : criteria) {
            preds.add(toPredicate(c));
        }

        return rec -> {
            for (var p : preds) {
                if (!p.test(rec)) return false;
            }
            return true;
        };
    }

    private Predicate<GenericRecord> toPredicate(FilterCriterion c) {
        var op = c.op();
        Object raw = c.value();
        String expected = raw != null ? String.valueOf(raw) : null;

        if (c.isWildcard()) {
            // Wildcard: DFS the entire record
            return rec -> DeepSearchEngine.matches(rec, expected, op);
        }

        // Specific field: DFS into that field's subtree (handles nested records, arrays, maps)
        String fieldName = c.fieldName();
        return rec -> DeepSearchEngine.matches(rec.get(fieldName), expected, op);
    }
}



