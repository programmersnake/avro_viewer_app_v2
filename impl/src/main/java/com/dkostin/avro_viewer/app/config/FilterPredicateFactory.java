package com.dkostin.avro_viewer.app.config;

import com.dkostin.avro_viewer.app.domain.model.filter.FilterCriterion;
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
        var op = c.op();
        Object raw = c.value();

        return rec -> op.matches(rec.get(field), raw);
    }
}
