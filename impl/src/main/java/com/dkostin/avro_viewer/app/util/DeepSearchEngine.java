package com.dkostin.avro_viewer.app.util;

import lombok.experimental.UtilityClass;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.IndexedRecord;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * In-memory DFS search engine that natively traverses both Avro types
 * ({@link IndexedRecord}, {@link org.apache.avro.generic.GenericArray})
 * and normalized Java types ({@link Map}, {@link Collection}).
 * <p>
 * Does <b>not</b> use {@link AvroNormalizer} — operates directly on the
 * Avro object graph to avoid GC thrashing on large datasets.
 */
@UtilityClass
public final class DeepSearchEngine {

    /**
     * Tests whether any leaf value reachable from {@code node} satisfies
     * the compiled {@code matcher}, taking the schema context into account
     * to resolve logical decimals.
     */
    public static boolean matches(Object node, Schema schema, PreparedMatcher matcher) {
        if (node == null) {
            return matcher.matches(null);
        }

        Schema unwrappedSchema = unwrapUnion(schema);

        // Handle Logical Decimal wrapped in ByteBuffer, Fixed, or byte[]
        if (unwrappedSchema != null && unwrappedSchema.getLogicalType() instanceof org.apache.avro.LogicalTypes.Decimal dec) {
            byte[] bytes = null;
            if (node instanceof byte[] b) {
                bytes = b;
            } else if (node instanceof ByteBuffer bb) {
                ByteBuffer dup = bb.duplicate();
                bytes = new byte[dup.remaining()];
                dup.get(bytes);
            } else if (node instanceof GenericData.Fixed f) {
                bytes = f.bytes();
            }
            if (bytes != null) {
                BigDecimal bd = new BigDecimal(new java.math.BigInteger(bytes), dec.getScale());
                return matcher.matches(bd);
            }
        }

        // Avro record (GenericRecord): iterate field values
        if (node instanceof IndexedRecord rec) {
            Schema recSchema = unwrappedSchema != null ? unwrappedSchema : rec.getSchema();
            List<Schema.Field> fields = recSchema.getFields();
            for (int i = 0; i < fields.size(); i++) {
                Schema.Field f = fields.get(i);
                if (matches(rec.get(f.pos()), f.schema(), matcher)) {
                    return true;
                }
            }
            return false;
        }

        // Map (Avro MAP type or normalized Map): recurse into values only
        if (node instanceof Map<?, ?> map) {
            Schema valSchema = unwrappedSchema != null && unwrappedSchema.getType() == Schema.Type.MAP
                    ? unwrappedSchema.getValueType()
                    : null;
            for (Object v : map.values()) {
                if (matches(v, valSchema, matcher)) {
                    return true;
                }
            }
            return false;
        }

        // Collection/Array (GenericArray extends Collection, or java.util.List)
        if (node instanceof Collection<?> coll) {
            Schema elemSchema = unwrappedSchema != null && unwrappedSchema.getType() == Schema.Type.ARRAY
                    ? unwrappedSchema.getElementType()
                    : null;
            for (Object item : coll) {
                if (matches(item, elemSchema, matcher)) {
                    return true;
                }
            }
            return false;
        }

        // Binary data: not searchable if not logical decimal
        if (node instanceof ByteBuffer || node instanceof GenericData.Fixed || node instanceof byte[]) {
            return false;
        }

        // Leaf value: delegate to PreparedMatcher (handles Number, CharSequence/Utf8, Enum, etc.)
        return matcher.matches(node);
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



