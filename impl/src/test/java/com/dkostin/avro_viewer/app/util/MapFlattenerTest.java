package com.dkostin.avro_viewer.app.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapFlattenerTest {

    @Test
    void testFlattenBasics() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("name", "Alice");
        input.put("age", 30);
        input.put("balance", new BigDecimal("100.000"));

        Map<String, Object> output = new LinkedHashMap<>();
        MapFlattener.flatten("", input, output);

        assertEquals("Alice", output.get("name"));
        assertEquals("30", output.get("age"));
        assertEquals("100", output.get("balance"));
    }

    @Test
    void testFlattenNestedMap() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("level1", Map.of("level2", "value"));

        Map<String, Object> output = new LinkedHashMap<>();
        MapFlattener.flatten("", input, output);

        assertEquals("value", output.get("level1.level2"));
    }

    @Test
    void testFlattenCollections() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("primitiveList", Arrays.asList("A", "B", "C"));
        input.put("complexList", List.of(Map.of("k", "v")));
        input.put("emptyList", Collections.emptyList());

        Map<String, Object> output = new LinkedHashMap<>();
        MapFlattener.flatten("", input, output);

        assertEquals("A|B|C", output.get("primitiveList"));
        assertEquals("", output.get("emptyList"));
        assertEquals("[{\"k\":\"v\"}]", output.get("complexList")); // compact JSON expected
    }
}
