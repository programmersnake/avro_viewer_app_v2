package com.dkostin.avro_viewer.app.util;

import lombok.experimental.UtilityClass;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Flattens nested Map structures into a single-level Map with dot-separated keys.
 * <p>
 * Uses a shared {@link StringBuilder} for path construction to minimize GC pressure
 * from ephemeral String concatenation on deeply nested or high-volume records.
 */
@UtilityClass
public final class MapFlattener {

    /**
     * Public entry point — delegates to the StringBuilder-backed implementation.
     */
    public static void flatten(String currentPath, Object node, Map<String, Object> flatRow) {
        StringBuilder pathBuilder = new StringBuilder(currentPath);
        flattenInternal(pathBuilder, node, flatRow);
    }

    /**
     * Internal recursive implementation using a mutable StringBuilder for path tracking.
     * The StringBuilder is extended before each recursive call and truncated back (via
     * {@code setLength}) afterwards, avoiding per-call String allocation.
     */
    private static void flattenInternal(StringBuilder pathBuilder, Object node, Map<String, Object> flatRow) {
        switch (node) {
            case null -> {
                if (!pathBuilder.isEmpty()) {
                    flatRow.put(pathBuilder.toString(), "");
                }
                return;
            }

            // Nested Map
            case Map<?, ?> map -> {
                int savedLen = pathBuilder.length();
                map.forEach((k, v) -> {
                    pathBuilder.setLength(savedLen);
                    if (savedLen > 0) {
                        pathBuilder.append('.');
                    }
                    pathBuilder.append(k);
                    flattenInternal(pathBuilder, v, flatRow);
                });
                pathBuilder.setLength(savedLen);
                return;
            }

            // Arrays / Collections
            case Collection<?> coll -> {
                String currentPath = pathBuilder.toString();
                if (coll.isEmpty()) {
                    flatRow.put(currentPath, "");
                    return;
                }
                // A collection is "primitive" ONLY IF every element is a simple type
                boolean isPrimitive = coll.stream().allMatch(e ->
                        e instanceof CharSequence || e instanceof Number
                                || e instanceof Boolean || e instanceof Enum);
                if (isPrimitive) {
                    // Join with | (O(n) via Collectors.joining)
                    String joined = coll.stream()
                            .map(PresentationFormatter::formatValue)
                            .collect(Collectors.joining("|"));
                    flatRow.put(currentPath, joined);
                } else {
                    // Complex objects -> JSON
                    try {
                        flatRow.put(currentPath, JsonSerializer.toCompactJson(coll));
                    } catch (Exception e) {
                        flatRow.put(currentPath, coll.toString());
                    }
                }
                return;
            }
            default -> {
            }
        }

        // Fallback for numbers, booleans etc.
        flatRow.put(pathBuilder.toString(), PresentationFormatter.formatValue(node));
    }
}
