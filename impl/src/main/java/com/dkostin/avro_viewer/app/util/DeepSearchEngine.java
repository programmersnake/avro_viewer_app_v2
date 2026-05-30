package com.dkostin.avro_viewer.app.util;

import lombok.experimental.UtilityClass;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.IndexedRecord;

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
     * the compiled {@code matcher}.
     *
     * <ul>
     *   <li>{@link IndexedRecord} (GenericRecord): recurses into each field value.</li>
     *   <li>{@link Map}: recurses into <b>values only</b> (not keys).</li>
     *   <li>{@link Collection} (GenericArray, List): recurses into each element.</li>
     *   <li>Binary ({@link ByteBuffer}, {@link GenericData.Fixed}, byte[]): returns false.</li>
     *   <li>Leaves (String, Number, Boolean, Utf8, Enum): delegates to
     *       {@link PreparedMatcher#matches(Object)}.</li>
     * </ul>
     */
    public static boolean matches(Object node, PreparedMatcher matcher) {
        if (node == null) {
            return matcher.matches(null);
        }

        // Avro record (GenericRecord): iterate field values
        if (node instanceof IndexedRecord rec) {
            List<Schema.Field> fields = rec.getSchema().getFields();
            for (int i = 0; i < fields.size(); i++) {
                if (matches(rec.get(fields.get(i).pos()), matcher)) {
                    return true;
                }
            }
            return false;
        }

        // Map (Avro MAP type or normalized Map): recurse into values only
        if (node instanceof Map<?, ?> map) {
            for (Object v : map.values()) {
                if (matches(v, matcher)) {
                    return true;
                }
            }
            return false;
        }

        // Collection/Array (GenericArray extends Collection, or java.util.List)
        if (node instanceof Collection<?> coll) {
            for (Object item : coll) {
                if (matches(item, matcher)) {
                    return true;
                }
            }
            return false;
        }

        // Binary data: not searchable
        if (node instanceof ByteBuffer || node instanceof GenericData.Fixed || node instanceof byte[]) {
            return false;
        }

        // Leaf value: delegate to PreparedMatcher (handles Number, CharSequence/Utf8, Enum, etc.)
        return matcher.matches(node);
    }
}



