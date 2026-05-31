package com.dkostin.avro_viewer.app.config;

import com.dkostin.avro_viewer.app.util.StructuralFlatteningEngine;

/**
 * Configuration options for the {@link StructuralFlatteningEngine}.
 *
 * @param deepFlattening        If true, flattens recursively (Dot-Notation Mode).
 *                              If false, only keeps primary root-level fields and serializes
 *                              any nested complex structures as JSON inline (Isolate Objects).
 * @param indexBasedSuffixing   If true, unpacks array indices as columns (array.0.key, array.1.key).
 *                              If false, serializes nested arrays directly to inline JSON.
 */
public record FlatteningConfig(
    boolean deepFlattening,
    boolean indexBasedSuffixing
) {
    public FlatteningConfig {
        if (!deepFlattening) {
            indexBasedSuffixing = false;
        }
    }
}
