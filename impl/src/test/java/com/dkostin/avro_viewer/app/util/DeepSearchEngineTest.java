package com.dkostin.avro_viewer.app.util;

import com.dkostin.avro_viewer.app.domain.model.filter.MatchOperation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepSearchEngineTest {

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
    void matchesMapKeys() {
        var row = Map.of("specialKey", "irrelevant");
        assertTrue(DeepSearchEngine.matches(row, "specialKey", MatchOperation.CONTAINS));
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
}
