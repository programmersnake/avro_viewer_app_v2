package com.dkostin.avro_viewer.app.domain.model;

import org.apache.avro.Schema;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record SearchResult(
        Schema schema,
        List<Map<String, Object>> records,
        StopReason stopReason,
        long scanned,
        double fractionScanned
) {
    public SearchResult {
        records = records == null ? List.of() : Collections.unmodifiableList(records);
        if (stopReason == null) stopReason = StopReason.COMPLETED;
        if (scanned < 0) scanned = 0;
        if (Double.isNaN(fractionScanned) || fractionScanned < 0.0) fractionScanned = 0.0;
        if (fractionScanned > 1.0) fractionScanned = 1.0;
    }

    public SearchResult(Schema schema, List<Map<String, Object>> records, StopReason stopReason, long scanned) {
        this(schema, records, stopReason, scanned, 1.0);
    }

    public SearchResult(Schema schema, List<Map<String, Object>> records, boolean truncated, long scanned) {
        this(schema, records, truncated ? StopReason.MAX_RESULTS : StopReason.COMPLETED, scanned, 1.0);
    }

    public boolean truncated() {
        return stopReason != StopReason.COMPLETED;
    }
}
