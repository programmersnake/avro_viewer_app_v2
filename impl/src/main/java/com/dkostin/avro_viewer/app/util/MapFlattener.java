package com.dkostin.avro_viewer.app.util;

import lombok.experimental.UtilityClass;

import java.util.Collection;
import java.util.Map;

@UtilityClass
public final class MapFlattener {

    public static void flatten(String currentPath, Object node, Map<String, Object> flatRow) {
        switch (node) {
            case null -> {
                if (!currentPath.isEmpty()) {
                    flatRow.put(currentPath, "");
                }
                return;
            }


            // Nested Map
            case Map<?, ?> map -> {
                map.forEach((k, v) -> {
                    String newPath = currentPath.isEmpty() ? String.valueOf(k) : currentPath + "." + k;
                    flatten(newPath, v, flatRow);
                });
                return;
            }


            // Arrays / Collections
            case Collection<?> coll -> {
                if (coll.isEmpty()) {
                    flatRow.put(currentPath, "");
                    return;
                }
                // Check if it's an array of primitives
                Object first = coll.iterator().next();
                boolean isPrimitive = first instanceof CharSequence || first instanceof Number || first instanceof Boolean || first instanceof Enum;
                if (isPrimitive) {
                    // Join with |
                    String joined = coll.stream().map(PresentationFormatter::formatValue).reduce((a, b) -> a + "|" + b).orElse("");
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
        flatRow.put(currentPath, PresentationFormatter.formatValue(node));
    }
}
