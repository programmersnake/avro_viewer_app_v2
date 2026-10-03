package com.dkostin.avro_viewer.app.domain.model;

import com.dkostin.avro_viewer.app.domain.model.filter.FilterGroup;
import org.apache.avro.Schema;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Immutable search inputs, captured on the FX thread.
 */
public record SearchRequest(Path file, Schema schema, List<FilterGroup> groups, int maxResults) {
    public SearchRequest {
        Objects.requireNonNull(file, "file cannot be null");
        groups = groups == null ? List.of() : List.copyOf(groups);
    }
}
