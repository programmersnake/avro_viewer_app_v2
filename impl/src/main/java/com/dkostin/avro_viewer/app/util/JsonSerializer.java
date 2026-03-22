package com.dkostin.avro_viewer.app.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
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
            return COMPACT_MAPPER.writeValueAsString(toSerializable(object));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize object to JSON", e);
        }
    }

    public static String toJsonSafe(Object object) {
        try {
            return MAPPER.writeValueAsString(toSerializable(object));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize object to JSON", e);
        }
    }

    /**
     * Converts container types (e.g. JavaFX ObservableList, custom Map impls) to standard
     * Java collections that Jackson can serialize without module-system reflection issues.
     * <p>
     * Unlike the old normalizeObject, this does NOT touch leaf values — that responsibility
     * belongs to {@link AvroNormalizer} at the data boundary.
     */
    private static Object toSerializable(Object object) {
        if (object == null) {
            return null;
        }

        if (object instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>(map.size());
            for (var e : map.entrySet()) {
                out.put(String.valueOf(e.getKey()), toSerializable(e.getValue()));
            }
            return out;
        }

        if (object instanceof Collection<?> coll) {
            var list = new ArrayList<>(coll.size());
            for (Object item : coll) {
                list.add(toSerializable(item));
            }
            return list;
        }

        // Leaf values (String, Number, Boolean, etc.) — already normalized by AvroNormalizer
        return object;
    }

}

