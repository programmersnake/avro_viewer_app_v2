package com.dkostin.avro_viewer.app.service.impl;

import com.dkostin.avro_viewer.app.config.FilterPredicateFactory;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterCriterion;
import com.dkostin.avro_viewer.app.service.api.RecordProvider;
import com.dkostin.avro_viewer.app.util.AvroNormalizer;
import com.dkostin.avro_viewer.app.util.JsonSerializer;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.SeekableFileInput;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Predicate;

public class AvroRecordProvider implements RecordProvider {

    private final DataFileReader<GenericRecord> reader;
    private final Schema schema;
    private final Predicate<GenericRecord> predicate;
    private GenericRecord nextRecord;
    private boolean isClosed = false;

    public AvroRecordProvider(Path file, List<FilterCriterion> criteria, FilterPredicateFactory predicateFactory) throws IOException {
        Objects.requireNonNull(file, "file cannot be null");
        SeekableFileInput input = new SeekableFileInput(file.toFile());
        try {
            this.reader = new DataFileReader<>(input, new GenericDatumReader<>());
            this.schema = this.reader.getSchema();
            
            if (criteria != null && !criteria.isEmpty() && predicateFactory != null) {
                this.predicate = predicateFactory.compile(criteria);
            } else {
                this.predicate = r -> true;
            }
            advance();
        } catch (IOException | RuntimeException e) {
            try {
                input.close();
            } catch (IOException ignored) {}
            throw e;
        }
    }

    private void advance() {
        nextRecord = null;
        while (reader.hasNext()) {
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt(); // preserve interrupt status
                break;
            }
            GenericRecord rec = reader.next();
            if (predicate.test(rec)) {
                nextRecord = rec;
                break;
            }
        }
    }

    @Override
    public boolean hasNext() {
        return !isClosed && nextRecord != null;
    }

    @Override
    public String nextJsonRecord() throws IOException {
        if (!hasNext()) {
            throw new NoSuchElementException("No more records available");
        }
        GenericRecord rec = nextRecord;
        advance();
        Object normalized = AvroNormalizer.normalize(rec, schema);
        return JsonSerializer.toCompactJson(normalized);
    }

    @Override
    public void close() throws IOException {
        if (isClosed) return;
        isClosed = true;
        reader.close();
    }
}
