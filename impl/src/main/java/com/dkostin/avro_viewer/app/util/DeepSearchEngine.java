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
     *   <li>Leaves (String, Number, Boolean, Enum): formatted via
     *       {@link PresentationFormatter#formatValue(Object)} then evaluated
     *       with {@code operation.matches(formatted, expectedQuery)}.</li>
     *   <li>Maps: recursively checks both keys (as strings) and values.</li>
     *   <li>Collections/Arrays: recursively checks each element with short-circuiting.</li>
     *   <li>Null: returns {@code false} (unless the operation is IS_NULL).</li>
     * </ul>
     */
    public static boolean matches(Object node, String expectedQuery, MatchOperation operation) {
        // Null-check ops are handled at the top level only
        if (operation == MatchOperation.IS_NULL) return node == null;
        if (operation == MatchOperation.NOT_NULL) return node != null;

        if (node == null) return false;

        // Map: check keys and values recursively
        if (node instanceof Map<?, ?> map) {
            return map.entrySet().stream().anyMatch(e ->
                    operation.matches(String.valueOf(e.getKey()), expectedQuery)
                            || matches(e.getValue(), expectedQuery, operation)
            );
        }

        // Collection: check each element recursively (short-circuits via anyMatch)
        if (node instanceof Collection<?> coll) {
            return coll.stream().anyMatch(item -> matches(item, expectedQuery, operation));
        }

        // Leaf value: format to the exact string the user sees in the UI, then match
        String formatted = PresentationFormatter.formatValue(node);
        return operation.matches(formatted, expectedQuery);
    }
}
