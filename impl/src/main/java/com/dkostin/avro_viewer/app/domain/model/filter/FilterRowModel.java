package com.dkostin.avro_viewer.app.domain.model.filter;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class FilterRowModel {
    private String field;
    private MatchOperation op = MatchOperation.CONTAINS;
    private String value;
}

