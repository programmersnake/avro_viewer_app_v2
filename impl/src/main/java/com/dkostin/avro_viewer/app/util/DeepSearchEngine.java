package com.dkostin.avro_viewer.app.util;

import com.dkostin.avro_viewer.app.domain.model.filter.MatchOperation;
import lombok.experimental.UtilityClass;

import java.util.Collection;
import java.util.Map;

/**
 * In-memory DFS search engine for normalized Avro data structures.
 * Recursively traverses Maps, Collections, and leaf values without
 * JSON serialization to avoid GC thrashing on large datasets.
 */
@UtilityClass
public final class DeepSearchEngine {

    /**
     * Tests whether any leaf value reachable from {@code node} satisfies
     * the given {@code operation} against {@code expectedQuery}.
     *
     * <ul>
     *   <li>Leaves (String, Number, Boolean, Enum): passed directly to
     *       {@link MatchOperation#matches(Object, Object)} which handles
     *       string normalization (including BigDecimal formatting).</li>
     *   <li>Maps: recursively checks <b>values only</b> (not keys, to avoid
     *       false positives on schema field names).</li>
     *   <li>Collections/Arrays: recursively checks each element with short-circuiting.</li>
     *   <li>Null: returns {@code false} (unless the operation is IS_NULL).</li>
     * </ul>
     */
    public static boolean matches(Object node, String expectedQuery, MatchOperation operation) {
        // Null-check ops are handled at the top level only
        if (operation == MatchOperation.IS_NULL) return node == null;
        if (operation == MatchOperation.NOT_NULL) return node != null;

        if (node == null) return false;

        // Map: recurse into values only (not keys — avoids matching schema field names)
        if (node instanceof Map<?, ?> map) {
            return map.values().stream()
                    .anyMatch(v -> matches(v, expectedQuery, operation));
        }

        // Collection: check each element recursively (short-circuits via anyMatch)
        if (node instanceof Collection<?> coll) {
            return coll.stream().anyMatch(item -> matches(item, expectedQuery, operation));
        }

        // Leaf value: delegate directly to MatchOperation (handles BigDecimal, CharSequence, etc.)
        return operation.matches(node, expectedQuery);
    }
}

