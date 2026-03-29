package com.dkostin.avro_viewer.app.util;

import com.dkostin.avro_viewer.app.domain.model.filter.MatchOperation;
import lombok.experimental.UtilityClass;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.IndexedRecord;

import java.nio.ByteBuffer;
import java.util.Collection;
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
     * the given {@code operation} against {@code expectedQuery}.
     *
     * <ul>
     *   <li>{@link IndexedRecord} (GenericRecord): recurses into each field value.</li>
     *   <li>{@link Map}: recurses into <b>values only</b> (not keys).</li>
     *   <li>{@link Collection} (GenericArray, List): recurses into each element.</li>
     *   <li>Binary ({@link ByteBuffer}, {@link GenericData.Fixed}, byte[]): returns false.</li>
     *   <li>Leaves (String, Number, Boolean, Utf8, Enum): delegates to
     *       {@link MatchOperation#matches(Object, Object)}.</li>
     * </ul>
     */
    public static boolean matches(Object node, String expectedQuery, MatchOperation operation) {
        // Null-check ops at the top level only
        if (operation == MatchOperation.IS_NULL) return node == null;
        if (operation == MatchOperation.NOT_NULL) return node != null;

        if (node == null) return false;

        // Avro record (GenericRecord): iterate field values
        if (node instanceof IndexedRecord rec) {
            return rec.getSchema().getFields().stream()
                    .anyMatch(f -> matches(rec.get(f.pos()), expectedQuery, operation));
        }

        // Map (Avro MAP type or normalized Map): recurse into values only
        if (node instanceof Map<?, ?> map) {
            return map.values().stream()
                    .anyMatch(v -> matches(v, expectedQuery, operation));
        }

        // Collection/Array (GenericArray extends Collection, or java.util.List)
        if (node instanceof Collection<?> coll) {
            return coll.stream()
                    .anyMatch(item -> matches(item, expectedQuery, operation));
        }

        // Binary data: not searchable
        if (node instanceof ByteBuffer || node instanceof GenericData.Fixed || node instanceof byte[]) {
            return false;
        }

        // Leaf value: delegate to MatchOperation (handles Number, CharSequence/Utf8, Enum, etc.)
        return operation.matches(node, expectedQuery);
    }
}


