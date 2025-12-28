package com.dkostin.avro_viewer.app.data;

import com.dkostin.avro_viewer.app.domain.Page;
import com.dkostin.avro_viewer.app.domain.filter.FilterCriterion;
import com.dkostin.avro_viewer.app.config.FilterPredicateFactory;
import lombok.RequiredArgsConstructor;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.SeekableFileInput;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class AvroFileServiceImpl implements AvroFileService { // todo TODO.md {2}

    private final FilterPredicateFactory predicateFactory;

    @Override
    public Schema readSchema(Path file) throws IOException {
        try (DataFileReader<GenericRecord> r = open(file)) {
            return r.getSchema();
        }
    }

    @Override
    public Page readPage(Path file, int pageIndex, int pageSize) throws IOException {
        long start = (long) pageIndex * pageSize;

        try (DataFileReader<GenericRecord> r = open(file)) {
            Schema schema = r.getSchema();

            // skip to start
            long skipped = 0;
            while (skipped < start && r.hasNext()) {
                r.next();
                skipped++;
            }

            // read page
            List<GenericRecord> out = new ArrayList<>(pageSize);
            int read = 0;
            while (read < pageSize && r.hasNext()) {
                out.add(r.next());
                read++;
            }

            return new Page(schema, out, r.hasNext());
        }
    }

    private DataFileReader<GenericRecord> open(Path file) throws IOException {
        return new DataFileReader<>(
                new SeekableFileInput(new File(file.toString())),
                new GenericDatumReader<>()
        );
    }

    @Override
    public SearchResult search(Path file, List<FilterCriterion> criteria, int maxResults) throws Exception {
        if (file == null) throw new IllegalArgumentException("file is null");
        if (maxResults <= 0) throw new IllegalArgumentException("maxResults must be > 0");

        var predicate = predicateFactory.compile(criteria);

        List<GenericRecord> out = new java.util.ArrayList<>(Math.min(maxResults, 1024));
        long scanned = 0;
        boolean truncated = false;

        try (var reader = new org.apache.avro.file.DataFileReader<>(
                file.toFile(),
                new org.apache.avro.generic.GenericDatumReader<GenericRecord>()
        )) {
            Schema schema = reader.getSchema();

            while (reader.hasNext()) {
                GenericRecord rec = reader.next();
                scanned++;

                if (predicate.test(rec)) {
                    out.add(rec);

                    if (out.size() >= maxResults) {
                        truncated = true;
                        break;
                    }
                }
            }

            return new SearchResult(schema, out, truncated, scanned);
        }
    }
}
