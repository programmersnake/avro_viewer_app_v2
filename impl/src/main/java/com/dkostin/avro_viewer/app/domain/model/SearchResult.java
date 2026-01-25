package com.dkostin.avro_viewer.app.domain.model;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;

import java.util.List;

public record SearchResult(
        Schema schema,
        List<GenericRecord> records,
        boolean truncated,   // true if stop for maxResults
        long scanned         // how much record were checked
) {
}
