package com.dkostin.avro_viewer.app.domain.model.filter;

/**
 * A single filter criterion: which field to search, the match operation, and the expected value.
 */
public record FilterCriterion(FilterOption field, MatchOperation op, Object value) {

    /** Returns true if this criterion targets all fields (deep recursive search). */
    public boolean isWildcard() {
        return field != null && field.wildcard();
    }

    /** Returns the schema field name, or {@code null} if the field is unset. */
    public String fieldName() {
        return field != null ? field.fieldName() : null;
    }
}

