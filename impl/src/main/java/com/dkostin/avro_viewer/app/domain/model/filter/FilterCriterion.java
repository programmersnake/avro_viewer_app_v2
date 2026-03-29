package com.dkostin.avro_viewer.app.domain.model.filter;

public record FilterCriterion(String field, MatchOperation op, Object value) {

    /** Sentinel field value meaning "search all fields recursively". Uses '*' which is not a valid Avro field name. */
    public static final String ANY_FIELD = "*";

    /** Returns true if this criterion targets all fields (deep recursive search). */
    public boolean isWildcard() {
        return ANY_FIELD.equals(field);
    }
}
