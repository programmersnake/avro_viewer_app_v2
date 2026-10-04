package com.dkostin.avro_viewer.app.service.impl;

import com.dkostin.avro_viewer.app.config.FilterPredicateFactory;
import com.dkostin.avro_viewer.app.domain.model.Page;
import com.dkostin.avro_viewer.app.domain.model.SearchControl;
import com.dkostin.avro_viewer.app.domain.model.SearchProgress;
import com.dkostin.avro_viewer.app.domain.model.SearchResult;
import com.dkostin.avro_viewer.app.domain.model.StopReason;
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
import java.util.concurrent.atomic.AtomicBoolean;

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

    @Test
    void testReadFileInfoWithMetadata(@TempDir Path tempDir) throws IOException {
        Path avroFile = tempDir.resolve("info.avro");
        try (DataFileWriter<GenericRecord> writer = new DataFileWriter<>(new GenericDatumWriter<>(schema))) {
            writer.setMeta("custom.author", "Alice");
            writer.setMeta("custom.binary", new byte[]{0x00, 0x01, 0x02});
            writer.create(schema, avroFile.toFile());
            writer.append(createRecord("1", 42));
        }

        var info = fileService.readFileInfo(avroFile);
        assertNotNull(info);
        assertEquals(avroFile.toAbsolutePath().normalize(), info.path());
        assertTrue(info.sizeBytes() > 0);
        assertNotNull(info.lastModified());
        assertEquals("null", info.codec());
        assertEquals(schema.toString(), info.schema().toString());
        assertEquals("Alice", info.metadata().get("custom.author"));
        assertEquals("<3 bytes, binary>", info.metadata().get("custom.binary"));
    }

    @Test
    void testCountRecordsAndCaching(@TempDir Path tempDir) throws IOException {
        Path avroFile = tempDir.resolve("count.avro");
        try (DataFileWriter<GenericRecord> writer = new DataFileWriter<>(new GenericDatumWriter<>(schema))) {
            writer.create(schema, avroFile.toFile());
            for (int i = 0; i < 75; i++) {
                writer.append(createRecord("id-" + i, i));
                if (i % 25 == 24) {
                    writer.sync();
                }
            }
        }

        assertTrue(fileService.knownRecordCount(avroFile).isEmpty());

        long count = fileService.countRecords(avroFile);
        assertEquals(75, count);

        assertTrue(fileService.knownRecordCount(avroFile).isPresent());
        assertEquals(75, fileService.knownRecordCount(avroFile).getAsLong());

        fileService.invalidate();
        assertTrue(fileService.knownRecordCount(avroFile).isEmpty());
    }

    @Test
    void testRepositionSessionToPageWithBlockIndexMatchesSequential(@TempDir Path tempDir) throws IOException {
        Path avroFile = tempDir.resolve("seek.avro");
        List<GenericRecord> records = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            records.add(createRecord("rec-" + i, i));
        }

        try (DataFileWriter<GenericRecord> writer = new DataFileWriter<>(new GenericDatumWriter<>(schema))) {
            writer.create(schema, avroFile.toFile());
            for (int i = 0; i < records.size(); i++) {
                writer.append(records.get(i));
                if (i % 20 == 19) {
                    writer.sync();
                }
            }
        }

        // Populate block index cache first
        fileService.countRecords(avroFile);

        // Jump to page 5 (pageSize = 10, startRecord = 50)
        Page jumpedPage = fileService.readPage(avroFile, 5, 10);
        assertEquals(10, jumpedPage.records().size());
        assertEquals("rec-50", jumpedPage.records().getFirst().get("id").toString());
        assertEquals("rec-59", jumpedPage.records().getLast().get("id").toString());

        // Subsequent page 6 should continue sequentially
        Page nextSeqPage = fileService.readPage(avroFile, 6, 10);
        assertEquals(10, nextSeqPage.records().size());
        assertEquals("rec-60", nextSeqPage.records().getFirst().get("id").toString());

        // Jump back to page 2 (startRecord = 20)
        Page jumpBackPage = fileService.readPage(avroFile, 2, 10);
        assertEquals(10, jumpBackPage.records().size());
        assertEquals("rec-20", jumpBackPage.records().getFirst().get("id").toString());
    }

    @Test
    void testSearchCompletedReturnsAllMatches(@TempDir Path tempDir) throws Exception {
        Path avroFile = tempDir.resolve("search_complete.avro");
        List<GenericRecord> records = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            records.add(createRecord("id-" + i, i));
        }
        writeAvroFile(avroFile, schema, records);

        List<FilterGroup> groups = List.of(new FilterGroup(List.of(
                new FilterCriterion(FilterOption.ofField("value"), MatchOperation.EQUALS, "5")
        )));

        SearchResult result = fileService.search(avroFile, groups, 100);
        assertNotNull(result);
        assertEquals(StopReason.COMPLETED, result.stopReason());
        assertFalse(result.truncated());
        assertEquals(1, result.records().size());
        assertEquals(50, result.scanned());
        assertEquals(1.0, result.fractionScanned());
    }

    @Test
    void testSearchMaxResultsTruncates(@TempDir Path tempDir) throws Exception {
        Path avroFile = tempDir.resolve("search_max.avro");
        List<GenericRecord> records = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            records.add(createRecord("id-" + i, i));
        }
        writeAvroFile(avroFile, schema, records);

        List<FilterGroup> groups = List.of(new FilterGroup(List.of(
                new FilterCriterion(FilterOption.ALL_FIELDS, MatchOperation.CONTAINS, "id")
        )));

        SearchResult result = fileService.search(avroFile, groups, 5);
        assertNotNull(result);
        assertEquals(StopReason.MAX_RESULTS, result.stopReason());
        assertTrue(result.truncated());
        assertEquals(5, result.records().size());
        assertEquals(5, result.scanned());
    }

    @Test
    void testSearchUserStoppedReturnsPartialResults(@TempDir Path tempDir) throws Exception {
        Path avroFile = tempDir.resolve("search_stopped.avro");
        List<GenericRecord> records = new ArrayList<>();
        for (int i = 0; i < 3000; i++) {
            records.add(createRecord("id-" + i, i));
        }
        writeAvroFile(avroFile, schema, records);

        List<FilterGroup> groups = List.of(new FilterGroup(List.of(
                new FilterCriterion(FilterOption.ALL_FIELDS, MatchOperation.CONTAINS, "id")
        )));

        AtomicBoolean stopRequested = new AtomicBoolean(false);
        SearchControl control = new SearchControl(stopRequested, progress -> {
            if (progress.scanned() >= 1000) {
                stopRequested.set(true);
            }
        });

        SearchResult result = fileService.search(avroFile, groups, 5000, control);
        assertNotNull(result);
        assertEquals(StopReason.USER_STOPPED, result.stopReason());
        assertTrue(result.truncated());
        assertTrue(result.records().size() >= 1000 && result.records().size() < 3000);
        assertTrue(result.scanned() >= 1000 && result.scanned() < 3000);
    }

    @Test
    void testSearchReportsMonotonicProgress(@TempDir Path tempDir) throws Exception {
        Path avroFile = tempDir.resolve("search_progress.avro");
        List<GenericRecord> records = new ArrayList<>();
        for (int i = 0; i < 2500; i++) {
            records.add(createRecord("id-" + i, i));
        }
        writeAvroFile(avroFile, schema, records);

        List<FilterGroup> groups = List.of(new FilterGroup(List.of(
                new FilterCriterion(FilterOption.ofField("value"), MatchOperation.EQUALS, "999")
        )));

        List<SearchProgress> progressReports = new ArrayList<>();
        SearchControl control = new SearchControl(new AtomicBoolean(false), progressReports::add);

        SearchResult result = fileService.search(avroFile, groups, 100, control);
        assertNotNull(result);
        assertEquals(StopReason.COMPLETED, result.stopReason());
        assertFalse(progressReports.isEmpty(), "Should report progress at least once");

        long lastScanned = -1;
        for (SearchProgress p : progressReports) {
            assertTrue(p.scanned() >= lastScanned, "Progress must be monotonic in scanned records");
            assertTrue(p.fraction() >= 0.0 && p.fraction() <= 1.0, "Fraction must be within [0.0, 1.0]");
            lastScanned = p.scanned();
        }
        assertEquals(2500, result.scanned());
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
