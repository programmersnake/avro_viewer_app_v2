package com.dkostin.avro_viewer.app.domain.model.filter;

import java.util.List;

/**
 * A group of filter criteria combined with AND logic.
 * Multiple groups are combined with OR logic (Disjunctive Normal Form).
 */
public record FilterGroup(List<FilterCriterion> criteria) {

    public FilterGroup {
        criteria = List.copyOf(criteria);
    }

    public boolean isEmpty() {
        return criteria.isEmpty();
    }
}
