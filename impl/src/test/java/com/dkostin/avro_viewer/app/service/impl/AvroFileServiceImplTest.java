package com.dkostin.avro_viewer.app.service.impl;

import com.dkostin.avro_viewer.app.config.FilterPredicateFactory;
import com.dkostin.avro_viewer.app.domain.model.Page;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterCriterion;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterGroup;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterOption;
import com.dkostin.avro_viewer.app.domain.model.filter.MatchOperation;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AvroFileServiceImplTest {

    private AvroFileServiceImpl fileService;
    private Schema schema;

    @BeforeEach
    void setUp() {
        fileService = new AvroFileServiceImpl(new FilterPredicateFactory());
        schema = SchemaBuilder.record("Item").fields()
                .requiredString("id")
                .requiredInt("value")
                .endRecord();
    }

    @Test
    void testMissingFileThrowsException(@TempDir Path tempDir) {
        Path nonExistent = tempDir.resolve("missing.avro");
        assertThrows(NoSuchFileException.class, () -> fileService.readPage(nonExistent, 0, 10));
    }

    @Test
    void testFileReplacementWithSameTimestampDetected(@TempDir Path tempDir) throws IOException {
        Path avroFile = tempDir.resolve("data.avro");

        // Create initial file with 2 records
        List<GenericRecord> initialRecords = new ArrayList<>();
        GenericRecord r1 = new GenericData.Record(schema);
        r1.put("id", "A");
        r1.put("value", 10);
        initialRecords.add(r1);

        writeAvroFile(avroFile, schema, initialRecords);
        FileTime initialTime = Files.getLastModifiedTime(avroFile);

        Page page1 = fileService.readPage(avroFile, 0, 10);
        assertEquals(1, page1.records().size());
        assertEquals("A", page1.records().getFirst().get("id").toString());

        // Replace file with different records (different size)
        List<GenericRecord> newRecords = new ArrayList<>();
        GenericRecord r2 = new GenericData.Record(schema);
        r2.put("id", "B");
        r2.put("value", 20);
        newRecords.add(r2);
        GenericRecord r3 = new GenericData.Record(schema);
        r3.put("id", "C");
        r3.put("value", 30);
        newRecords.add(r3);

        writeAvroFile(avroFile, schema, newRecords);
        // Force the same last modified timestamp
        Files.setLastModifiedTime(avroFile, initialTime);

        // Read again: FileIdentity should detect change because size/fileKey changed
        Page page2 = fileService.readPage(avroFile, 0, 10);
        assertEquals(2, page2.records().size(), "Cache must be invalidated when file size/identity changes despite same timestamp");
        assertEquals("B", page2.records().getFirst().get("id").toString());
    }

    @Test
    void testExplicitInvalidateClearsCache(@TempDir Path tempDir) throws IOException {
        Path avroFile = tempDir.resolve("data.avro");

        List<GenericRecord> records = List.of(createRecord("1", 100));
        writeAvroFile(avroFile, schema, records);

        Page page = fileService.readPage(avroFile, 0, 10);
        assertNotNull(page);

        // Calling invalidate should clear cache without errors
        assertDoesNotThrow(() -> fileService.invalidate());
    }

    @Test
    void testSearchInterruptionThrowsInterruptedIOException(@TempDir Path tempDir) throws IOException {
        Path avroFile = tempDir.resolve("search.avro");

        List<GenericRecord> records = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            records.add(createRecord("id-" + i, i));
        }
        writeAvroFile(avroFile, schema, records);

        List<FilterGroup> groups = List.of(new FilterGroup(List.of(
                new FilterCriterion(FilterOption.ALL_FIELDS, MatchOperation.CONTAINS, "id")
        )));

        try {
            Thread.currentThread().interrupt();

            assertThrows(InterruptedIOException.class, () ->
                    fileService.search(avroFile, groups, 100));

            assertTrue(Thread.interrupted(), "Interrupt flag must be set");
        } finally {
            Thread.interrupted();
        }
    }

    private GenericRecord createRecord(String id, int value) {
        GenericRecord rec = new GenericData.Record(schema);
        rec.put("id", id);
        rec.put("value", value);
        return rec;
    }

    private void writeAvroFile(Path path, Schema schema, List<GenericRecord> records) throws IOException {
        try (DataFileWriter<GenericRecord> writer = new DataFileWriter<>(new GenericDatumWriter<>(schema))) {
            writer.create(schema, path.toFile());
            for (GenericRecord rec : records) {
                writer.append(rec);
            }
        }
    }
}
