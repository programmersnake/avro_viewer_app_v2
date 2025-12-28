package com.dkostin.avro_viewer.app.domain.filter;

public record FilterCriterion(String field, MatchOperation op, Object value) {
}
