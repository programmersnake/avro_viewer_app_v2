package com.dkostin.avro_viewer.app.filter;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class FilterRowModel {
    private String field;
    private MatchOperation op = MatchOperation.CONTAINS;
    private String value;

    public boolean isEmpty() {
        return isEmpty(field) && isEmpty(value);
    }

    private boolean isEmpty(String s) {
        return s == null || s.isBlank();
    }
}

