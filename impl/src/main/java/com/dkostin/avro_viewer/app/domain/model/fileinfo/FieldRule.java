package com.dkostin.avro_viewer.app.domain.model.fileinfo;

import java.util.Objects;

/**
 * Key-value descriptor of a schema rule or constraint (e.g. default value, enum symbols, precision/scale).
 */
public record FieldRule(String label, String value) {
    public FieldRule {
        Objects.requireNonNull(label, "label cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
    }
}
