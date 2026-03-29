package com.dkostin.avro_viewer.app.config;

import com.dkostin.avro_viewer.app.domain.model.filter.FilterCriterion;
import com.dkostin.avro_viewer.app.util.AvroNormalizer;
import com.dkostin.avro_viewer.app.util.DeepSearchEngine;
import org.apache.avro.generic.GenericRecord;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class FilterPredicateFactory {

    /**
     * Compiles filter criteria into a predicate on {@link GenericRecord}.
     * <p>
     * Two-phase evaluation strategy:
     * <ol>
     *   <li><b>Simple predicates</b> (specific field): evaluate directly on GenericRecord
     *       via {@code rec.get(field)} — zero allocation.</li>
     *   <li><b>Deep predicates</b> (wildcard, {@link FilterCriterion#isWildcard()}):
     *       normalize the record inline via {@link AvroNormalizer}, then delegate
     *       to {@link DeepSearchEngine} for recursive DFS traversal.</li>
     * </ol>
     * Simple predicates are tested first to short-circuit cheap rejections before
     * paying the normalization cost of deep predicates.
     */
    public Predicate<GenericRecord> compile(List<FilterCriterion> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            return _ -> true;
        }

        // Split into simple (direct field access) and deep (wildcard → normalize + DFS)
        List<Predicate<GenericRecord>> simplePreds = new java.util.ArrayList<>();
        List<Predicate<GenericRecord>> deepPreds = new java.util.ArrayList<>();

        for (FilterCriterion c : criteria) {
            if (c.isWildcard()) {
                deepPreds.add(deepPredicate(c));
            } else {
                simplePreds.add(simplePredicate(c));
            }
        }

        // AND: simple first (cheap), then deep (expensive) only if simple all pass
        return rec -> {
            for (var p : simplePreds) {
                if (!p.test(rec)) return false;
            }
            for (var p : deepPreds) {
                if (!p.test(rec)) return false;
            }
            return true;
        };
    }

    /**
     * Specific field: direct GenericRecord access, zero normalization overhead.
     */
    private Predicate<GenericRecord> simplePredicate(FilterCriterion c) {
        var op = c.op();
        String field = c.field();
        Object raw = c.value();

        return rec -> op.matches(rec.get(field), raw);
    }

    /**
     * Wildcard: normalizes the entire record inline, then uses DeepSearchEngine
     * for recursive DFS. Only pays normalization cost per-record, and only
     * after all simple predicates have passed.
     */
    @SuppressWarnings("unchecked")
    private Predicate<GenericRecord> deepPredicate(FilterCriterion c) {
        var op = c.op();
        Object raw = c.value();
        String expected = raw != null ? String.valueOf(raw) : null;

        return rec -> {
            Map<String, Object> normalized =
                    (Map<String, Object>) AvroNormalizer.normalize(rec, rec.getSchema());
            return DeepSearchEngine.matches(normalized, expected, op);
        };
    }
}


