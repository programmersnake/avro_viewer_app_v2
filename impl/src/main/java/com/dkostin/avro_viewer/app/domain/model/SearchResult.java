package com.dkostin.avro_viewer.app.domain.model;

import org.apache.avro.Schema;

import java.util.List;
import java.util.Map;

public record SearchResult(
        Schema schema,
        List<Map<String, Object>> records,
        boolean truncated,   // true if stop for maxResults
        long scanned         // how much record were checked
) {
}

