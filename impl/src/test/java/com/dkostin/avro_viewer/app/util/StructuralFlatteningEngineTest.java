package com.dkostin.avro_viewer.app.util;

import com.dkostin.avro_viewer.app.config.FlatteningConfig;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StructuralFlatteningEngineTest {

    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    @Test
    void testDeepFlatteningWithArraySuffixing() throws Exception {
        String json = """
            {
                "id": 123,
                "user": {
                    "name": "Denis",
                    "details": {
                        "age": 30,
                        "decimal": 12.3400
                    }
                },
                "tags": ["a", "b"]
            }
            """;
        JsonNode node = mapper.readTree(json);
        FlatteningConfig config = new FlatteningConfig(true, true);
        Map<String, String> flat = StructuralFlatteningEngine.flatten(node, config);

        assertEquals("123", flat.get("id"));
        assertEquals("Denis", flat.get("user.name"));
        assertEquals("30", flat.get("user.details.age"));
        assertEquals("12.34", flat.get("user.details.decimal"));
        assertEquals("a", flat.get("tags.0"));
        assertEquals("b", flat.get("tags.1"));
    }

    @Test
    void testDeepFlatteningWithoutArraySuffixing() throws Exception {
        String json = """
            {
                "user": {
                    "name": "Denis"
                },
                "tags": ["a", "b"]
            }
            """;
        JsonNode node = mapper.readTree(json);
        FlatteningConfig config = new FlatteningConfig(true, false);
        Map<String, String> flat = StructuralFlatteningEngine.flatten(node, config);

        assertEquals("Denis", flat.get("user.name"));
        assertEquals("[\"a\",\"b\"]", flat.get("tags"));
    }

    @Test
    void testIsolateObjectsMode() throws Exception {
        String json = """
            {
                "id": 123,
                "user": {
                    "name": "Denis",
                    "age": 30
                },
                "tags": ["a", "b"]
            }
            """;
        JsonNode node = mapper.readTree(json);
        FlatteningConfig config = new FlatteningConfig(false, true); // array suffixing ignored in isolate objects mode
        Map<String, String> flat = StructuralFlatteningEngine.flatten(node, config);

        assertEquals("123", flat.get("id"));
        assertEquals("{\"name\":\"Denis\",\"age\":30}", flat.get("user"));
        assertEquals("[\"a\",\"b\"]", flat.get("tags"));
    }

    @Test
    void testNameCollisionDeduplication() throws Exception {
        String json = """
            {
                "parent.name": "collision-root",
                "parent": {
                    "name": "collision-nested"
                }
            }
            """;
        JsonNode node = mapper.readTree(json);
        FlatteningConfig config = new FlatteningConfig(true, true);
        Map<String, String> flat = StructuralFlatteningEngine.flatten(node, config);

        // Keys should be deduplicated by appending _1, _2 etc.
        assertEquals("collision-root", flat.get("parent.name"));
        assertEquals("collision-nested", flat.get("parent.name_1"));
    }
}
