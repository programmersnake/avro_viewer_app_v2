package com.dkostin.avro_viewer.app.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@UtilityClass
public final class JsonSerializer {

    private static final ObjectMapper MAPPER;
    private static final ObjectMapper COMPACT_MAPPER;

    static {
        SimpleModule module = new SimpleModule();
        module.addSerializer(java.math.BigDecimal.class, new ValueSerializer<java.math.BigDecimal>() {
            @Override
            public void serialize(java.math.BigDecimal value, JsonGenerator gen, SerializationContext serializers) {
                gen.writeNumber(PresentationFormatter.formatBigDecimal(value));
            }
        });

        MAPPER = JsonMapper.builder()
                .findAndAddModules()
                .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .addModule(module)
                .build();

        COMPACT_MAPPER = JsonMapper.builder()
                .findAndAddModules()
                .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
                .addModule(module)
                .build();
    }

    public static String toCompactJson(Object object) {
        try {
            return COMPACT_MAPPER.writeValueAsString(toSerializable(object));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize object to JSON", e);
        }
    }

    public static String toJsonSafe(Object object) {
        try {
            return MAPPER.writeValueAsString(toSerializable(object));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize object to JSON", e);
        }
    }

    /**
     * Converts container types (e.g. JavaFX ObservableList, custom Map impls) to standard
     * Java collections that Jackson can serialize without module-system reflection issues.
     * Also applies a defensive fallback for any non-standard leaf types that might have
     * slipped through AvroNormalizer.
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

        // Standard Jackson-serializable leaf types — pass through
        if (object instanceof String || object instanceof Number || object instanceof Boolean) {
            return object;
        }

        // Defensive fallback: stringify any non-standard types that Jackson can't handle natively
        // (e.g. Avro GenericEnumSymbol, Utf8, or other types AvroNormalizer didn't fully convert)
        log.debug("toSerializable: converting unknown type {} to String via valueOf()", object.getClass().getName());
        return String.valueOf(object);
    }

}
