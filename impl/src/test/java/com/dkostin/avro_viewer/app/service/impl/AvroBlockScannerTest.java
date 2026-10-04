package com.dkostin.avro_viewer.app.service.impl;

import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.file.CodecFactory;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.file.SeekableFileInput;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AvroBlockScannerTest {

    private Schema schema;

    @BeforeEach
    void setUp() {
        schema = SchemaBuilder.record("Sample").fields()
                .requiredInt("id")
                .requiredString("payload")
                .endRecord();
    }

    @Test
    void testMissingFileThrowsNoSuchFileException(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("missing.avro");
        assertThrows(NoSuchFileException.class, () -> AvroBlockScanner.scan(missing));
    }

    @Test
    void testEmptyFileHasZeroRecords(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("empty.avro");
        try (DataFileWriter<GenericRecord> writer = new DataFileWriter<>(new GenericDatumWriter<>(schema))) {
            writer.create(schema, file.toFile());
        }

        BlockIndex index = AvroBlockScanner.scan(file);
        assertEquals(0, index.totalRecords());
        assertEquals(0, index.blockOffsets().length);
        assertEquals(0, index.firstRecordIndex().length);
        assertThrows(IndexOutOfBoundsException.class, () -> index.blockContaining(0));
    }

    @Test
    void testSingleBlockFile(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("single_block.avro");
        try (DataFileWriter<GenericRecord> writer = new DataFileWriter<>(new GenericDatumWriter<>(schema))) {
            writer.create(schema, file.toFile());
            for (int i = 0; i < 50; i++) {
                GenericRecord rec = new GenericData.Record(schema);
                rec.put("id", i);
                rec.put("payload", "str_" + i);
                writer.append(rec);
            }
        }

        BlockIndex index = AvroBlockScanner.scan(file);
        assertEquals(50, index.totalRecords());
        assertEquals(1, index.blockOffsets().length);
        assertEquals(0, index.firstRecordIndex()[0]);
        assertEquals(0, index.blockContaining(0));
        assertEquals(0, index.blockContaining(25));
        assertEquals(0, index.blockContaining(49));
        assertThrows(IndexOutOfBoundsException.class, () -> index.blockContaining(50));
    }

    @Test
    void testMultipleBlocksWithVariousCodecs(@TempDir Path tempDir) throws IOException {
        CodecFactory[] codecs = new CodecFactory[]{
                CodecFactory.nullCodec(),
                CodecFactory.deflateCodec(CodecFactory.DEFAULT_DEFLATE_LEVEL),
                CodecFactory.snappyCodec()
        };

        for (CodecFactory codec : codecs) {
            Path file = tempDir.resolve("multi_block_" + codec.toString() + ".avro");
            int totalRecordsExpected = 300;

            try (DataFileWriter<GenericRecord> writer = new DataFileWriter<>(new GenericDatumWriter<>(schema))) {
                writer.setCodec(codec);
                writer.create(schema, file.toFile());
                for (int i = 0; i < totalRecordsExpected; i++) {
                    GenericRecord rec = new GenericData.Record(schema);
                    rec.put("id", i);
                    rec.put("payload", "large_payload_string_to_fill_bytes_" + i);
                    writer.append(rec);
                    if ((i + 1) % 50 == 0) {
                        writer.sync();
                    }
                }
            }

            BlockIndex index = AvroBlockScanner.scan(file);
            assertEquals(totalRecordsExpected, index.totalRecords(), "Total count must match for codec " + codec);
            assertTrue(index.blockOffsets().length >= 6, "Expected at least 6 blocks, got: " + index.blockOffsets().length);

            // Verify block offsets work directly with DataFileReader.seek()
            try (DataFileReader<GenericRecord> reader = new DataFileReader<>(new SeekableFileInput(file.toFile()), new GenericDatumReader<>())) {
                for (int b = 0; b < index.blockOffsets().length; b++) {
                    long offset = index.blockOffsets()[b];
                    long expectedFirstRecord = index.firstRecordIndex()[b];
                    assertEquals(b, index.blockContaining(expectedFirstRecord));

                    reader.seek(offset);
                    assertTrue(reader.hasNext());
                    GenericRecord rec = reader.next();
                    assertEquals((int) expectedFirstRecord, rec.get("id"), "Record at block boundary must match expected id");
                }
            }
        }
    }

    @Test
    void testCorruptedSyncMarkerThrowsIOException(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("corrupted.avro");
        try (DataFileWriter<GenericRecord> writer = new DataFileWriter<>(new GenericDatumWriter<>(schema))) {
            writer.create(schema, file.toFile());
            for (int i = 0; i < 20; i++) {
                GenericRecord rec = new GenericData.Record(schema);
                rec.put("id", i);
                rec.put("payload", "test");
                writer.append(rec);
            }
            writer.sync();
        }

        // Corrupt the last 5 bytes of the file (part of the final sync marker)
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            long len = raf.length();
            raf.seek(len - 5);
            raf.write(new byte[]{0x00, 0x01, 0x02, 0x03, 0x04});
        }

        IOException ex = assertThrows(IOException.class, () -> AvroBlockScanner.scan(file));
        assertTrue(ex.getMessage().contains("sync marker mismatch") || ex.getMessage().contains("Corrupted"),
                "Exception message should mention corruption/sync marker: " + ex.getMessage());
    }

    @Test
    void testInterruptedThreadThrowsInterruptedIOException(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("interrupted.avro");
        try (DataFileWriter<GenericRecord> writer = new DataFileWriter<>(new GenericDatumWriter<>(schema))) {
            writer.create(schema, file.toFile());
            for (int i = 0; i < 50; i++) {
                GenericRecord rec = new GenericData.Record(schema);
                rec.put("id", i);
                rec.put("payload", "test");
                writer.append(rec);
                writer.sync();
            }
        }

        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedIOException.class, () -> AvroBlockScanner.scan(file));
        } finally {
            Thread.interrupted(); // clear interrupted status
        }
    }
}
