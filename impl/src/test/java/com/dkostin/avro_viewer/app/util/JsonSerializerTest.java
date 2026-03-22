package com.dkostin.avro_viewer.app.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonSerializerTest {

    @Test
    void testToCompactJson() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("amount", new BigDecimal("1000.00"));
        input.put("name", "Test");

        String json = JsonSerializer.toCompactJson(input);

        assertFalse(json.contains("\n"), "Compact JSON should not contain newlines");
        assertTrue(json.contains("\"amount\":1000"), "BigDecimals should be stripped in output");
        assertFalse(json.contains("1000.00"));
    }

    @Test
    void testToJsonSafe() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("amount", new BigDecimal("500.500"));

        String json = JsonSerializer.toJsonSafe(input);

        assertTrue(json.contains("\n"), "Safe JSON should contain indentation/newlines");
        assertTrue(json.contains("500.5"), "BigDecimals should be stripped of trailing zeros");
        assertFalse(json.contains("500.500"));
    }

    @Test
    void testByteEncoding() {
        // Data is now pre-normalized by AvroNormalizer at the boundary
        Map<String, Object> normalized = Map.of("__bytes_b64__", java.util.Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}));
        String json = JsonSerializer.toCompactJson(normalized);
        assertTrue(json.contains("__bytes_b64__"), "Bytes should be serialized with __bytes_b64__ key");
    }
}
