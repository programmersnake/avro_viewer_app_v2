package com.dkostin.avro_viewer.app.domain.model.filter;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum MatchOperation {
    EQUALS("equals"),
    CONTAINS("contains"),
    STARTS_WITH("starts with"),
    ENDS_WITH("ends with"),
    IS_NULL("is null"),
    NOT_NULL("not null");

    private final String label;

    @Override
    public String toString() {
        return label;
    }
}
