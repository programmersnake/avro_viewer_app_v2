package com.dkostin.avro_viewer.app.domain.model;

public record SearchProgress(long scanned, long matched, double fraction) {
    public SearchProgress {
        if (scanned < 0) scanned = 0;
        if (matched < 0) matched = 0;
        if (Double.isNaN(fraction) || fraction < 0.0) fraction = 0.0;
        if (fraction > 1.0) fraction = 1.0;
    }
}
