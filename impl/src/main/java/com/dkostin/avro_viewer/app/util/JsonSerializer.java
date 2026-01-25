package com.dkostin.avro_viewer.app.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.experimental.UtilityClass;

import java.util.Map;

@UtilityClass
public final class JsonSerializer {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static String toJsonSafe(Object object) {
        try {
            return MAPPER.writeValueAsString(normalizeObject(object));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize object to JSON", e);
        }
    }

    private static Object normalizeObject(Object object) {
        if (object == null) {
            return null;
        }

        // simple types
        if (object instanceof String || object instanceof Number || object instanceof Boolean) {
            return object;
        }

        // Utf8 as CharSequence
        if (object instanceof CharSequence cs) {
            return cs.toString();
        }

        // enums
        if (object instanceof Enum<?> e) {
            return e.name();
        }

        // bytes
        if (object instanceof byte[] bytes) {
            return Map.of("__bytes_b64__", java.util.Base64.getEncoder().encodeToString(bytes));
        }
        if (object instanceof java.nio.ByteBuffer bb) {
            java.nio.ByteBuffer dup = bb.duplicate();
            byte[] bytes = new byte[dup.remaining()];
            dup.get(bytes);
            return Map.of("__bytes_b64__", java.util.Base64.getEncoder().encodeToString(bytes));
        }

        // Avro record
        if (object instanceof org.apache.avro.generic.GenericRecord rec) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            for (org.apache.avro.Schema.Field f : rec.getSchema().getFields()) {
                m.put(f.name(), normalizeObject(rec.get(f.name())));
            }
            return m;
        }

        // Avro array
        if (object instanceof org.apache.avro.generic.GenericArray<?> ga) {
            java.util.List<Object> list = new java.util.ArrayList<>();
            for (Object x : ga) list.add(normalizeObject(x));
            return list;
        }

        // collections
        if (object instanceof java.util.Collection<?> c) {
            java.util.List<Object> list = new java.util.ArrayList<>(c.size());
            for (Object x : c) list.add(normalizeObject(x));
            return list;
        }

        // maps
        if (object instanceof java.util.Map<?, ?> map) {
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            for (var e : map.entrySet()) {
                // ключі в JSON мають бути string
                String key = String.valueOf(e.getKey());
                out.put(key, normalizeObject(e.getValue()));
            }
            return out;
        }

        // Avro fixed / bytes logical
        if (object instanceof org.apache.avro.generic.GenericData.Fixed fixed) {
            byte[] bytes = fixed.bytes();
            return Map.of("__fixed_b64__", java.util.Base64.getEncoder().encodeToString(bytes));
        }

        // fallback: stringify
        return String.valueOf(object);
    }

}
