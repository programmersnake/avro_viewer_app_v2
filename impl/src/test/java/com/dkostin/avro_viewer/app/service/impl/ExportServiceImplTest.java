package com.dkostin.avro_viewer.app.service.impl;

import javafx.collections.FXCollections;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ExportServiceImplTest {

    @Test
    void testExportTableToCsvComplexAvro() throws IOException {
        ExportServiceImpl service = new ExportServiceImpl();
        Path tempFile = Files.createTempFile("export", ".csv");

        // Decimal schema setup: scale = 2
        Schema decimalSchema = Schema.create(Schema.Type.BYTES);
        LogicalTypes.decimal(10, 2).addToSchema(decimalSchema);

        Schema schema = SchemaBuilder.record("TestData")
                .fields()
                .name("level1").type().record("Level1").fields()
                    .name("level2").type(decimalSchema).noDefault()
                .endRecord().noDefault()
                .name("stringArray").type().array().items().stringType().noDefault()
                .name("objectArray").type().array().items().map().values().stringType().noDefault()
                .endRecord();

        // Data setup
        Map<String, Object> level1Data = new LinkedHashMap<>();
        Map<String, Object> level2Data = new LinkedHashMap<>();
        
        // As per prompt: `level1 -> level2 -> bytes_b64 : "BG8bOYGdVKZQ"`
        level2Data.put("bytes_b64", "BG8bOYGdVKZQ");
        level1Data.put("level2", level2Data);

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
        service.exportTableToCsv(tempFile, FXCollections.observableArrayList(row), schema);

        // Read back
        List<String> lines = Files.readAllLines(tempFile);
        assertEquals(2, lines.size(), "Should have header and one row");

        String header = lines.get(0);
        String data = lines.get(1);

        // Assert column headers are flattened using dot notation
        assertTrue(header.contains("level1.level2"), "Header must contain level1.level2");
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
}
