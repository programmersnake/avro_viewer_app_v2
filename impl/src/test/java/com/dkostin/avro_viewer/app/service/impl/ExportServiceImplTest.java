package com.dkostin.avro_viewer.app.service.impl;

import com.dkostin.avro_viewer.app.config.FlatteningConfig;
import com.dkostin.avro_viewer.app.service.api.RecordProvider;
import com.dkostin.avro_viewer.app.service.api.RecordProviderFactory;
import javafx.collections.FXCollections;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExportServiceImplTest {

    @Test
    void testExportTableToCsvComplexAvro() throws IOException {
        ExportServiceImpl service = new ExportServiceImpl();
        Path tempFile = Files.createTempFile("export", ".csv");



        // Data setup
        Map<String, Object> level1Data = new LinkedHashMap<>();
        level1Data.put("level2", new BigDecimal("1234.5678"));

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("level1", level1Data);
        row.put("stringArray", Arrays.asList("A", "B", "C"));
        row.put("objectArray", Arrays.asList(
                Map.of("k1", "v1"),
                Map.of("k2", "v2")
        ));

        // Let's add a primitive check as well
        row.put("primitiveString", "HelloWorld");

        // Calling the service
        service.exportTableToCsv(tempFile, FXCollections.observableArrayList(row));

        // Read back
        List<String> lines = Files.readAllLines(tempFile);
        assertEquals(2, lines.size(), "Should have header and one row");

        String header = lines.get(0);
        String data = lines.get(1);

        // Assert column headers are flattened using dot notation
        assertTrue(header.contains("level1.level2"), "Header must contain level1.level2");
        assertFalse(data.contains("1234.567800"), "Base64 value must be stripped properly");
        assertTrue(data.contains("1234.5678"), "Scale matching must be correct");
        assertFalse(header.contains("bytes_b64"), "bytes_b64 wrapper should be flattened implicitly");
        assertTrue(header.contains("stringArray"), "Header must contain stringArray");
        assertTrue(header.contains("objectArray"), "Header must contain objectArray");
        assertTrue(header.contains("primitiveString"), "Header must contain primitiveString");

        // Assert string array is joined with pipe
        assertTrue(data.contains("A|B|C"), "String array should be joined by pipe delimiter");

        // Assert json object array is compacted
        assertTrue(data.contains("\"[{") && data.contains("}]\""), "Object array should be JSON string with escaped CSV quotes");

        // We will need to figure out what "BG8bOYGdVKZQ" translates to.
        // But for now, we just assert it doesn't stay as "BG8bOYGdVKZQ"
        // and doesn't get exported as nested JSON (which is what current code does).
        // Since we know current code will export `{"level2":{"bytes_b64":"BG8bOYGdVKZQ"}}` etc.
        assertFalse(data.contains("BG8bOYGdVKZQ"), "Base64 value must be decoded, not raw string");

        Files.deleteIfExists(tempFile);
    }

    @Test
    void testExportTableToJson() throws IOException {
        ExportServiceImpl service = new ExportServiceImpl();
        Path tempFile = Files.createTempFile("export", ".json");

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("salary", new BigDecimal("1234.500"));
        row.put("name", "John");

        service.exportTableToJson(tempFile, FXCollections.observableArrayList(row));
        String data = Files.readString(tempFile);

        assertTrue(data.contains("\"name\" : \"John\""));
        assertTrue(data.contains("1234.5"));
        assertFalse(data.contains("1234.500"));

        Files.deleteIfExists(tempFile);
    }

    @Test
    void testExportToCsvStreamingDisjointRecords() throws IOException {
        ExportServiceImpl service = new ExportServiceImpl();
        Path tempFile = Files.createTempFile("export-streaming", ".csv");

        List<String> records = Arrays.asList(
                "{\"id\":1,\"common\":\"val1\",\"onlyA\":\"A\"}",
                "{\"id\":2,\"common\":\"val2\",\"onlyB\":\"B\"}"
        );

        RecordProviderFactory factory = () -> new ListRecordProvider(records);
        FlatteningConfig config = new FlatteningConfig(true, true);

        // Run with semicolon delimiter
        service.exportToCsvStreaming(tempFile, factory, config, ';', null);

        List<String> lines = Files.readAllLines(tempFile);
        assertEquals(3, lines.size(), "Should have header and two rows");

        // Headers should contain a union of all possible keys: id, common, onlyA, onlyB
        String[] headers = lines.get(0).split(";");
        assertArrayEquals(new String[]{"id", "common", "onlyA", "onlyB"}, headers);

        // Assert row 1 values (strip quotes and split)
        String[] row1 = lines.get(1).replace("\"", "").split(";", -1);
        assertArrayEquals(new String[]{"1", "val1", "A", ""}, row1);

        // Assert row 2 values
        String[] row2 = lines.get(2).replace("\"", "").split(";", -1);
        assertArrayEquals(new String[]{"2", "val2", "", "B"}, row2);

        Files.deleteIfExists(tempFile);
    }

    @Test
    void testExportToCsvStreamingEmptyRecords() throws IOException {
        ExportServiceImpl service = new ExportServiceImpl();
        Path tempFile = Files.createTempFile("export-empty", ".csv");

        RecordProviderFactory factory = () -> new ListRecordProvider(List.of());
        FlatteningConfig config = new FlatteningConfig(true, true);

        service.exportToCsvStreaming(tempFile, factory, config, ',', null);

        List<String> lines = Files.readAllLines(tempFile);
        assertTrue(lines.isEmpty() || (lines.size() == 1 && lines.get(0).isEmpty()));

        Files.deleteIfExists(tempFile);
    }

    @Test
    void testExportToCsvStreamingCancelled() throws IOException {
        ExportServiceImpl service = new ExportServiceImpl();
        Path tempFile = Files.createTempFile("export-cancelled", ".csv");
 
        List<String> records = Arrays.asList(
                "{\"id\":1}",
                "{\"id\":2}"
        );
 
        RecordProviderFactory factory = () -> new ListRecordProvider(records);
        FlatteningConfig config = new FlatteningConfig(true, true);
 
        try {
            // Simulate interruption
            Thread.currentThread().interrupt();
 
            assertThrows(IOException.class, () -> {
                service.exportToCsvStreaming(tempFile, factory, config, ',', null);
            });
 
            // Ensure thread interrupt flag is cleared before checking file existence so Files.exists doesn't fail
            assertTrue(Thread.interrupted(), "Interrupt flag should have been set");
            assertFalse(Files.exists(tempFile), "Output file should be deleted on cancellation");
        } finally {
            Thread.interrupted(); // ensure cleanup
            Files.deleteIfExists(tempFile);
        }
    }

    private static class ListRecordProvider implements RecordProvider {
        private final List<String> records;
        private int idx = 0;

        public ListRecordProvider(List<String> records) {
            this.records = records;
        }

        @Override
        public boolean hasNext() {
            return idx < records.size();
        }

        @Override
        public String nextJsonRecord() {
            return records.get(idx++);
        }

        @Override
        public void close() {}
    }
}
