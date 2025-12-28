package com.dkostin.avro_viewer.app.filter;

public record FilterCriterion(String field, MatchOperation op, Object value) {
}
