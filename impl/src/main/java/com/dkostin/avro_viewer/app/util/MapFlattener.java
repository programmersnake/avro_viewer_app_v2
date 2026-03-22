package com.dkostin.avro_viewer.app.util;

import lombok.experimental.UtilityClass;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

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
        flatRow.put(currentPath, PresentationFormatter.formatValue(node));
    }
}
