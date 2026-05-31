package com.dkostin.avro_viewer.app.service.api;

import java.io.IOException;

/**
 * Generic record iterator interface that decouples the flattening UI
 * and exporter from format-specific APIs (like Avro, Parquet, or Protobuf).
 */
public interface RecordProvider extends java.io.Closeable {
    /**
     * @return true if there are more records to read, false otherwise.
     */
    boolean hasNext();

    /**
     * Reads the next record and converts it to a standard compact JSON string representation.
     * @return String JSON record representation.
     * @throws IOException if reading the record fails.
     */
    String nextJsonRecord() throws IOException;
}
