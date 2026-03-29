package com.dkostin.avro_viewer.app.util;

import com.dkostin.avro_viewer.app.domain.model.filter.MatchOperation;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepSearchEngineTest {

    // --- Leaf tests (normalized data) ---

    @Test
    void matchesStringLeaf() {
        assertTrue(DeepSearchEngine.matches("hello world", "hello", MatchOperation.CONTAINS));
        assertTrue(DeepSearchEngine.matches("hello world", "hello world", MatchOperation.EQUALS));
        assertFalse(DeepSearchEngine.matches("hello world", "xyz", MatchOperation.CONTAINS));
    }

    @Test
    void matchesNumberLeaf() {
        assertTrue(DeepSearchEngine.matches(42, "42", MatchOperation.EQUALS));
        assertTrue(DeepSearchEngine.matches(new BigDecimal("50.000"), "50", MatchOperation.EQUALS));
    }

    @Test
    void matchesBooleanLeaf() {
        assertTrue(DeepSearchEngine.matches(true, "true", MatchOperation.EQUALS));
        assertFalse(DeepSearchEngine.matches(false, "true", MatchOperation.EQUALS));
    }

    @Test
    void matchesNull() {
        assertTrue(DeepSearchEngine.matches(null, null, MatchOperation.IS_NULL));
        assertFalse(DeepSearchEngine.matches("value", null, MatchOperation.IS_NULL));
        assertTrue(DeepSearchEngine.matches("value", null, MatchOperation.NOT_NULL));
        assertFalse(DeepSearchEngine.matches(null, null, MatchOperation.NOT_NULL));
    }

    // --- Container tests (normalized data) ---

    @Test
    void matchesDeepMap() {
        var row = Map.of(
                "name", "Alice",
                "nested", Map.of("city", "Prague", "code", 42)
        );
        assertTrue(DeepSearchEngine.matches(row, "Prague", MatchOperation.EQUALS));
        assertTrue(DeepSearchEngine.matches(row, "Alice", MatchOperation.CONTAINS));
        assertTrue(DeepSearchEngine.matches(row, "42", MatchOperation.EQUALS));
        assertFalse(DeepSearchEngine.matches(row, "Tokyo", MatchOperation.CONTAINS));
    }

    @Test
    void matchesDeepCollection() {
        var row = Map.of(
                "tags", List.of("alpha", "beta", "gamma"),
                "nested", Map.of("items", List.of(Map.of("id", "deep-value")))
        );
        assertTrue(DeepSearchEngine.matches(row, "beta", MatchOperation.EQUALS));
        assertTrue(DeepSearchEngine.matches(row, "deep-value", MatchOperation.CONTAINS));
        assertFalse(DeepSearchEngine.matches(row, "omega", MatchOperation.CONTAINS));
    }

    @Test
    void doesNotMatchMapKeys() {
        var row = Map.of("specialKey", "irrelevant");
        assertFalse(DeepSearchEngine.matches(row, "specialKey", MatchOperation.CONTAINS));
    }

    @Test
    void matchesMapValues() {
        var row = Map.of("field", "targetValue");
        assertTrue(DeepSearchEngine.matches(row, "targetValue", MatchOperation.CONTAINS));
    }

    @Test
    void startsWithAndEndsWith() {
        var row = Map.of("code", "INE0123456");
        assertTrue(DeepSearchEngine.matches(row, "INE", MatchOperation.STARTS_WITH));
        assertTrue(DeepSearchEngine.matches(row, "3456", MatchOperation.ENDS_WITH));
        assertFalse(DeepSearchEngine.matches(row, "XYZ", MatchOperation.STARTS_WITH));
    }

    @Test
    void nullNodeReturnsFalseForNonNullOps() {
        assertFalse(DeepSearchEngine.matches(null, "any", MatchOperation.CONTAINS));
        assertFalse(DeepSearchEngine.matches(null, "any", MatchOperation.EQUALS));
    }

    // --- Native Avro type traversal ---

    @Test
    void traversesGenericRecordNatively() {
        Schema schema = SchemaBuilder.record("TestRecord")
                .fields()
                .requiredString("name")
                .requiredInt("age")
                .endRecord();
        GenericRecord rec = new GenericData.Record(schema);
        rec.put("name", "Bob");
        rec.put("age", 30);

        assertTrue(DeepSearchEngine.matches(rec, "Bob", MatchOperation.EQUALS));
        assertTrue(DeepSearchEngine.matches(rec, "30", MatchOperation.EQUALS));
        assertFalse(DeepSearchEngine.matches(rec, "Alice", MatchOperation.CONTAINS));
    }

    @Test
    void traversesNestedGenericRecord() {
        Schema innerSchema = SchemaBuilder.record("Inner")
                .fields().requiredString("city").endRecord();
        Schema outerSchema = SchemaBuilder.record("Outer")
                .fields()
                .requiredString("name")
                .name("address").type(innerSchema).noDefault()
                .endRecord();

        GenericRecord inner = new GenericData.Record(innerSchema);
        inner.put("city", "Prague");
        GenericRecord outer = new GenericData.Record(outerSchema);
        outer.put("name", "Alice");
        outer.put("address", inner);

        // Deep search finds nested value
        assertTrue(DeepSearchEngine.matches(outer, "Prague", MatchOperation.EQUALS));
        // Also finds top-level value
        assertTrue(DeepSearchEngine.matches(outer, "Alice", MatchOperation.CONTAINS));
        // Does not match field names
        assertFalse(DeepSearchEngine.matches(outer, "city", MatchOperation.EQUALS));
    }

    @Test
    void skipsBinaryData() {
        assertFalse(DeepSearchEngine.matches(ByteBuffer.wrap(new byte[]{1, 2, 3}), "any", MatchOperation.CONTAINS));
        assertFalse(DeepSearchEngine.matches(new byte[]{1, 2, 3}, "any", MatchOperation.CONTAINS));
    }

    // --- Numeric comparison ---

    @Test
    void numericEqualsComparison() {
        // Mathematical equality: 50.000 == 50
        assertTrue(MatchOperation.EQUALS.matches(new BigDecimal("50.000"), "50"));
        assertTrue(MatchOperation.EQUALS.matches(42, "42"));
        assertTrue(MatchOperation.EQUALS.matches(3.14, "3.14"));
        // Non-numeric expected: falls back to string comparison
        assertFalse(MatchOperation.EQUALS.matches(42, "forty-two"));
    }
}


