package com.dkostin.avro_viewer.app.util;

import com.dkostin.avro_viewer.app.config.FlatteningConfig;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure, stateless engine that traverses JSON structures to convert hierarchical values
 * into flat key-value pairs suitable for CSV columns.
 */
@Slf4j
public final class StructuralFlatteningEngine {

    private StructuralFlatteningEngine() {
        // Stateless utility class
    }

    /**
     * Entry point to flatten a {@link JsonNode} according to the provided config.
     *
     * @param node   Root JSON node to flatten.
     * @param config Flattening options.
     * @return A flat Map mapping column path coordinates to string values.
     */
    public static Map<String, String> flatten(JsonNode node, FlatteningConfig config) {
        Map<String, String> flatRow = new LinkedHashMap<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return flatRow;
        }

        if (!config.deepFlattening()) {
            // Isolate Objects Mode: Only root-level keys are columns.
            if (node.isObject()) {
                node.fields().forEachRemaining(entry -> {
                    String key = entry.getKey();
                    JsonNode val = entry.getValue();
                    if (val.isNull()) {
                        putWithCollision(flatRow, key, "");
                    } else if (val.isValueNode()) {
                        putWithCollision(flatRow, key, val.asText());
                    } else {
                        // Serialize nested complex structure as JSON inline
                        putWithCollision(flatRow, key, val.toString());
                    }
                });
            } else {
                // Fallback for non-object root
                putWithCollision(flatRow, "value", node.toString());
            }
        } else {
            // Deep Flattening Mode: Recursive DFS traversal.
            flattenDeep("", node, flatRow, config.indexBasedSuffixing());
        }

        return flatRow;
    }

    /**
     * Recursive DFS traversal for deep flattening.
     */
    private static void flattenDeep(String currentPath, JsonNode node, Map<String, String> flatRow, boolean indexBasedSuffixing) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            if (!currentPath.isEmpty()) {
                putWithCollision(flatRow, currentPath, "");
            }
            return;
        }

        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String nextPath = currentPath.isEmpty() ? entry.getKey() : currentPath + "." + entry.getKey();
                flattenDeep(nextPath, entry.getValue(), flatRow, indexBasedSuffixing);
            });
        } else if (node.isArray()) {
            if (indexBasedSuffixing) {
                for (int i = 0; i < node.size(); i++) {
                    String nextPath = currentPath.isEmpty() ? String.valueOf(i) : currentPath + "." + i;
                    flattenDeep(nextPath, node.get(i), flatRow, indexBasedSuffixing);
                }
            } else {
                // Serialize array container node immediately to compact JSON
                putWithCollision(flatRow, currentPath, node.toString());
            }
        } else if (node.isValueNode()) {
            putWithCollision(flatRow, currentPath, node.asText());
        }
    }

    /**
     * Inserts keys into the flat map, resolving name collisions by appending dynamic numeric suffixes.
     */
    private static void putWithCollision(Map<String, String> flatRow, String baseKey, String value) {
        if (!flatRow.containsKey(baseKey)) {
            flatRow.put(baseKey, value);
        } else {
            int index = 1;
            String resolvedKey;
            do {
                resolvedKey = baseKey + "_" + index;
                index++;
            } while (flatRow.containsKey(resolvedKey));
            log.warn("Column name collision detected: path '{}' resolved to '{}'", baseKey, resolvedKey);
            flatRow.put(resolvedKey, value);
        }
    }
}
