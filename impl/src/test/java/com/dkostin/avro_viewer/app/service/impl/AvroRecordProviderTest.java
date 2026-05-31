package com.dkostin.avro_viewer.app.service.impl;

import com.dkostin.avro_viewer.app.config.FilterPredicateFactory;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterCriterion;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterOption;
import com.dkostin.avro_viewer.app.domain.model.filter.MatchOperation;
import com.dkostin.avro_viewer.app.service.api.RecordProvider;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AvroRecordProviderTest {

    private Path tempAvroFile;
    private Schema schema;
    private FilterPredicateFactory predicateFactory;

    @BeforeEach
    void setUp() throws IOException {
        predicateFactory = new FilterPredicateFactory();
        schema = SchemaBuilder.record("UserRecord")
                .fields()
                .name("id").type().intType().noDefault()
                .name("name").type().stringType().noDefault()
                .endRecord();

        tempAvroFile = Files.createTempFile("test-users", ".avro");

        try (DataFileWriter<GenericRecord> writer = new DataFileWriter<>(new GenericDatumWriter<>(schema))) {
            writer.create(schema, tempAvroFile.toFile());

            GenericRecord r1 = new GenericData.Record(schema);
            r1.put("id", 1);
            r1.put("name", "Alice");
            writer.append(r1);

            GenericRecord r2 = new GenericData.Record(schema);
            r2.put("id", 2);
            r2.put("name", "Bob");
            writer.append(r2);
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(tempAvroFile);
    }

    @Test
    void testBrowseAllRecords() throws IOException {
        try (RecordProvider provider = new AvroRecordProvider(tempAvroFile, Collections.emptyList(), predicateFactory)) {
            assertTrue(provider.hasNext());
            String r1 = provider.nextJsonRecord();
            assertTrue(r1.contains("\"id\":1") && r1.contains("\"name\":\"Alice\""));

            assertTrue(provider.hasNext());
            String r2 = provider.nextJsonRecord();
            assertTrue(r2.contains("\"id\":2") && r2.contains("\"name\":\"Bob\""));

            assertFalse(provider.hasNext());
        }
    }

    @Test
    void testPredicateFiltering() throws IOException {
        FilterCriterion criterion = new FilterCriterion(FilterOption.ofField("name"), MatchOperation.EQUALS, "Bob");
        try (RecordProvider provider = new AvroRecordProvider(tempAvroFile, List.of(criterion), predicateFactory)) {
            assertTrue(provider.hasNext());
            String r = provider.nextJsonRecord();
            assertTrue(r.contains("\"id\":2") && r.contains("\"name\":\"Bob\""));
            assertFalse(provider.hasNext());
        }
    }

    @Test
    void testInterruptionHandling() throws IOException {
        try (RecordProvider provider = new AvroRecordProvider(tempAvroFile, Collections.emptyList(), predicateFactory)) {
            assertTrue(provider.hasNext());
 
            // Force thread interrupt after construction
            Thread.currentThread().interrupt();
 
            // Reading the first record will trigger advance() to get the second record, which should break early due to interrupt
            String record = provider.nextJsonRecord();
            assertNotNull(record);
 
            assertFalse(provider.hasNext(), "Provider should return no more records when interrupted");
            assertTrue(Thread.interrupted(), "Interrupted status should be preserved");
        }
    }

    @Test
    void testCloseIdempotency() throws IOException {
        RecordProvider provider = new AvroRecordProvider(tempAvroFile, Collections.emptyList(), predicateFactory);
        provider.close();
        
        // subsequent closes should not crash
        assertDoesNotThrow(provider::close);
        assertFalse(provider.hasNext());
    }
}
