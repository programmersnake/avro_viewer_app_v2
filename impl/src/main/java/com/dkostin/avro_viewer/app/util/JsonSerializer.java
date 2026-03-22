package com.dkostin.avro_viewer.app.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.experimental.UtilityClass;

import java.util.Map;

@UtilityClass
public final class JsonSerializer {

    private static final ObjectMapper MAPPER;
    private static final ObjectMapper COMPACT_MAPPER;

    static {
        com.fasterxml.jackson.databind.module.SimpleModule module = new com.fasterxml.jackson.databind.module.SimpleModule();
        module.addSerializer(java.math.BigDecimal.class, new com.fasterxml.jackson.databind.JsonSerializer<java.math.BigDecimal>() {
            @Override
            public void serialize(java.math.BigDecimal value, com.fasterxml.jackson.core.JsonGenerator gen, com.fasterxml.jackson.databind.SerializerProvider serializers) throws java.io.IOException {
                gen.writeNumber(PresentationFormatter.formatBigDecimal(value));
            }
        });

        MAPPER = new ObjectMapper()
                .findAndRegisterModules()
                .enable(com.fasterxml.jackson.core.JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .registerModule(module);

        COMPACT_MAPPER = new ObjectMapper()
                .findAndRegisterModules()
                .enable(com.fasterxml.jackson.core.JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
                .registerModule(module);
    }

    public static String toCompactJson(Object object) {
        try {
            return COMPACT_MAPPER.writeValueAsString(normalizeObject(object));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize object to JSON", e);
        }
    }

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
        switch (object) {
            case CharSequence cs -> {
                return cs.toString();
            }


            // enums
            case Enum<?> e -> {
                return e.name();
            }


            // bytes
            case byte[] bytes -> {
                return Map.of("__bytes_b64__", java.util.Base64.getEncoder().encodeToString(bytes));
            }
            case java.nio.ByteBuffer bb -> {
                java.nio.ByteBuffer dup = bb.duplicate();
                byte[] bytes = new byte[dup.remaining()];
                dup.get(bytes);
                return Map.of("__bytes_b64__", java.util.Base64.getEncoder().encodeToString(bytes));
            }


            // collections
            case java.util.Collection<?> c -> {
                java.util.List<Object> list = new java.util.ArrayList<>(c.size());
                for (Object x : c) list.add(normalizeObject(x));
                return list;
            }


            // maps
            case Map<?, ?> map -> {
                Map<String, Object> out = new java.util.LinkedHashMap<>();
                for (var e : map.entrySet()) {
                    // ключі в JSON мають бути string
                    String key = String.valueOf(e.getKey());
                    out.put(key, normalizeObject(e.getValue()));
                }
                return out;
            }


            // Avro fixed / bytes logical
            case org.apache.avro.generic.GenericData.Fixed fixed -> {
                byte[] bytes = fixed.bytes();
                return Map.of("__fixed_b64__", java.util.Base64.getEncoder().encodeToString(bytes));
            }
            default -> {
            }
        }

        // fallback: stringify
        return String.valueOf(object);
    }

}
