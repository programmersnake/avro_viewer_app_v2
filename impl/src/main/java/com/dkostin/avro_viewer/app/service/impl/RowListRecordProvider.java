package com.dkostin.avro_viewer.app.service.impl;

import com.dkostin.avro_viewer.app.service.api.RecordProvider;
import com.dkostin.avro_viewer.app.util.JsonSerializer;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * RecordProvider implementation that streams over in-memory table rows.
 * Used for exporting the current view.
 */
public class RowListRecordProvider implements RecordProvider {
    private final Iterator<Map<String, Object>> iterator;

    public RowListRecordProvider(List<Map<String, Object>> rows) {
        this.iterator = (rows == null ? List.<Map<String, Object>>of() : rows).iterator();
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public String nextJsonRecord() {
        if (!hasNext()) {
            throw new NoSuchElementException("No more records available");
        }
        Map<String, Object> row = iterator.next();
        return JsonSerializer.toCompactJson(row);
    }

    @Override
    public void close() {
        // In-memory provider; no resources to release
    }
}
