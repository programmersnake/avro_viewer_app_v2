package com.dkostin.avro_viewer.app.service.impl;

import com.dkostin.avro_viewer.app.config.FlatteningConfig;
import com.dkostin.avro_viewer.app.service.api.RecordProvider;
import com.dkostin.avro_viewer.app.service.api.RecordProviderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.math.BigDecimal;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExportServiceImplTest {

    @Test
    void testExportTableToJson(@TempDir Path tempDir) throws IOException {
        ExportServiceImpl service = new ExportServiceImpl();
        Path destFile = tempDir.resolve("export.json");
        Files.writeString(destFile, "ORIGINAL_JSON");

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("salary", new BigDecimal("1234.500"));
        row.put("name", "John");

        service.exportTableToJson(destFile, List.of(row));
        String data = Files.readString(destFile);

        assertTrue(data.contains("\"name\" : \"John\""));
        assertTrue(data.contains("1234.5"));
        assertFalse(data.contains("1234.500"));

        assertNoPartFiles(tempDir);
    }

    @Test
    void testExportToCsvStreamingDisjointRecords(@TempDir Path tempDir) throws IOException {
        ExportServiceImpl service = new ExportServiceImpl();
        Path destFile = tempDir.resolve("export-streaming.csv");

        List<String> records = Arrays.asList(
                "{\"id\":1,\"common\":\"val1\",\"onlyA\":\"A\"}",
                "{\"id\":2,\"common\":\"val2\",\"onlyB\":\"B\"}"
        );

        RecordProviderFactory factory = () -> new ListRecordProvider(records);
        FlatteningConfig config = new FlatteningConfig(true, true);

        long written = service.exportToCsvStreaming(destFile, factory, config, ';', null);
        assertEquals(2, written);

        List<String> lines = Files.readAllLines(destFile);
        assertEquals(3, lines.size(), "Should have header and two rows");

        String[] headers = lines.get(0).split(";");
        assertArrayEquals(new String[]{"id", "common", "onlyA", "onlyB"}, headers);

        String[] row1 = lines.get(1).replace("\"", "").split(";", -1);
        assertArrayEquals(new String[]{"1", "val1", "A", ""}, row1);

        String[] row2 = lines.get(2).replace("\"", "").split(";", -1);
        assertArrayEquals(new String[]{"2", "val2", "", "B"}, row2);

        assertNoPartFiles(tempDir);
    }

    @Test
    void testExportToCsvStreamingEmptyRecords(@TempDir Path tempDir) throws IOException {
        ExportServiceImpl service = new ExportServiceImpl();
        Path destFile = tempDir.resolve("export-empty.csv");

        RecordProviderFactory factory = () -> new ListRecordProvider(List.of());
        FlatteningConfig config = new FlatteningConfig(true, true);

        long written = service.exportToCsvStreaming(destFile, factory, config, ',', null);
        assertEquals(0, written);

        List<String> lines = Files.readAllLines(destFile);
        assertTrue(lines.isEmpty() || (lines.size() == 1 && lines.getFirst().isEmpty()));

        assertNoPartFiles(tempDir);
    }

    @Test
    void testExportCancelledBeforeWritingPreservesExistingDestination(@TempDir Path tempDir) throws IOException {
        ExportServiceImpl service = new ExportServiceImpl();
        Path destFile = tempDir.resolve("existing-file.csv");
        Files.writeString(destFile, "ORIGINAL_DATA_PRESERVED");

        List<String> records = Arrays.asList("{\"id\":1}", "{\"id\":2}");
        RecordProviderFactory factory = () -> new ListRecordProvider(records);
        FlatteningConfig config = new FlatteningConfig(true, true);

        try {
            Thread.currentThread().interrupt();

            assertThrows(InterruptedIOException.class, () -> service.exportToCsvStreaming(destFile, factory, config, ',', null));

            assertTrue(Thread.interrupted(), "Interrupt flag should have been set");
            assertTrue(Files.exists(destFile), "Original destination file must remain intact");
            assertEquals("ORIGINAL_DATA_PRESERVED", Files.readString(destFile));
            assertNoPartFiles(tempDir);
        } finally {
            Thread.interrupted(); // clear status
        }
    }

    @Test
    void testExportCancelledDuringPass2PreservesExistingDestination(@TempDir Path tempDir) throws IOException {
        ExportServiceImpl service = new ExportServiceImpl();
        Path destFile = tempDir.resolve("existing-pass2.csv");
        Files.writeString(destFile, "ORIGINAL_DATA_PASS2");

        List<String> records = Arrays.asList("{\"id\":1}", "{\"id\":2}", "{\"id\":3}");

        // Factory that interrupts during pass 2 on the second record
        final int[] passCounter = {0};
        RecordProviderFactory factory = () -> {
            passCounter[0]++;
            final boolean isPass2 = (passCounter[0] == 2);
            return new RecordProvider() {
                private int idx = 0;

                @Override
                public boolean hasNext() {
                    return idx < records.size();
                }

                @Override
                public String nextJsonRecord() {
                    if (isPass2 && idx == 1) {
                        Thread.currentThread().interrupt();
                    }
                    return records.get(idx++);
                }

                @Override
                public void close() {}
            };
        };

        FlatteningConfig config = new FlatteningConfig(true, true);

        try {
            assertThrows(InterruptedIOException.class, () -> service.exportToCsvStreaming(destFile, factory, config, ',', null));

            assertTrue(Thread.interrupted(), "Interrupt flag should have been set");
            assertTrue(Files.exists(destFile), "Destination file must NOT be deleted or corrupted");
            assertEquals("ORIGINAL_DATA_PASS2", Files.readString(destFile));
            assertNoPartFiles(tempDir);
        } finally {
            Thread.interrupted();
        }
    }

    private void assertNoPartFiles(Path dir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.part")) {
            for (Path part : stream) {
                fail("Temporary file should have been cleaned up: " + part);
            }
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
