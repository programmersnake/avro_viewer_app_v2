package com.dkostin.avro_viewer.app.util;

import lombok.experimental.UtilityClass;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;

@UtilityClass
public final class AvroNormalizer {

    /**
     * Hard truncation limit for binary payloads (byte[] / ByteBuffer) before
     * Base64 encoding. Prevents excessive memory allocation when normalizing
     * large blobs that would otherwise produce multi-megabyte String objects
     * purely for table display.
     */
    private static final int BLOB_TRUNCATION_LIMIT = 1024;

    public static Object normalize(Object value, Schema schema) {
        if (value == null) return null;

        Schema actualSchema = unwrapUnion(schema);

        // GenericRecord
        if (value instanceof GenericRecord rec) {
            Map<String, Object> map = new LinkedHashMap<>();
            Schema recSchema = rec.getSchema();
            for (Schema.Field f : recSchema.getFields()) {
                map.put(f.name(), normalize(rec.get(f.pos()), f.schema()));
            }
            return map;
        }

        // Map
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            Schema valSchema = actualSchema != null && actualSchema.getType() == Schema.Type.MAP ? actualSchema.getValueType() : null;
            map.forEach((k, v) -> {
                out.put(String.valueOf(k), normalize(v, valSchema));
            });
            return out;
        }

        // Collection / Array
        if (value instanceof Collection<?> coll) {
            List<Object> out = new ArrayList<>(coll.size());
            Schema elemSchema = actualSchema != null && actualSchema.getType() == Schema.Type.ARRAY ? actualSchema.getElementType() : null;
            for (Object v : coll) {
                out.add(normalize(v, elemSchema));
            }
            return out;
        }

        // The Decimal Fix
        if (value instanceof byte[] || value instanceof ByteBuffer || value instanceof GenericData.Fixed) {
            byte[] bytes;
            if (value instanceof byte[] b) {
                bytes = b;
            } else if (value instanceof ByteBuffer bb) {
                ByteBuffer dup = bb.duplicate();
                bytes = new byte[dup.remaining()];
                dup.get(bytes);
            } else {
                bytes = ((GenericData.Fixed) value).bytes();
            }

            if (actualSchema != null && actualSchema.getLogicalType() instanceof LogicalTypes.Decimal dec) {
                return new BigDecimal(new BigInteger(bytes), dec.getScale());
            }

            // Fallback for raw bytes — truncate large blobs to limit GC pressure
            boolean wasTruncated = bytes.length > BLOB_TRUNCATION_LIMIT;
            byte[] toEncode = wasTruncated
                    ? java.util.Arrays.copyOf(bytes, BLOB_TRUNCATION_LIMIT)
                    : bytes;
            String encoded = Base64.getEncoder().encodeToString(toEncode);
            if (wasTruncated) {
                encoded += " ... [TRUNCATED]";
            }
            return Map.of("__bytes_b64__", encoded);
        }

        // Strings
        if (value instanceof CharSequence cs) {
            return cs.toString();
        }

        // Enums (Java enums)
        if (value instanceof Enum<?> e) {
            return e.name();
        }

        // Avro enums (GenericData.EnumSymbol does NOT extend java.lang.Enum)
        if (value instanceof org.apache.avro.generic.GenericEnumSymbol<?>) {
            return value.toString();
        }

        // Primitives fallback
        return value;
    }

    private static Schema unwrapUnion(Schema schema) {
        if (schema != null && schema.getType() == Schema.Type.UNION) {
            for (Schema s : schema.getTypes()) {
                if (s.getType() != Schema.Type.NULL) return s;
            }
        }
        return schema;
    }
}
