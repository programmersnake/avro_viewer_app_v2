package com.dkostin.avro_viewer.app.config;

import com.dkostin.avro_viewer.app.domain.model.InvalidFilterException;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterCriterion;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterGroup;
import org.apache.avro.Schema;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates filter paths against an Avro schema before search execution.
 */
public final class FilterPathValidator {

    private FilterPathValidator() {}

    /**
     * Validates all filter criteria across all groups against the given schema.
     * Throws {@link InvalidFilterException} if any field path does not exist in the schema.
     * Wildcards are skipped.
     */
    public static void validate(Schema root, List<FilterGroup> groups) {
        if (root == null || groups == null || groups.isEmpty()) {
            return;
        }

        Set<String> invalidPaths = new LinkedHashSet<>();
        for (FilterGroup group : groups) {
            if (group == null || group.isEmpty()) continue;
            for (FilterCriterion criterion : group.criteria()) {
                if (criterion == null || criterion.isWildcard()) {
                    continue;
                }
                String fieldName = criterion.fieldName();
                if (fieldName == null || fieldName.isBlank()) {
                    continue;
                }

                String[] segments = fieldName.split("\\.");
                if (!isValid(root, segments, 0)) {
                    invalidPaths.add(fieldName);
                }
            }
        }

        if (!invalidPaths.isEmpty()) {
            throw new InvalidFilterException(new ArrayList<>(invalidPaths));
        }
    }

    static boolean isValid(Schema schema, String[] path, int index) {
        if (schema == null) {
            return false;
        }
        if (index >= path.length) {
            return true;
        }

        if (schema.getType() == Schema.Type.UNION) {
            for (Schema branch : schema.getTypes()) {
                if (branch.getType() != Schema.Type.NULL) {
                    if (isValid(branch, path, index)) {
                        return true;
                    }
                }
            }
            return false;
        }

        String segment = path[index];

        if (schema.getType() == Schema.Type.RECORD) {
            Schema.Field f = schema.getField(segment);
            if (f == null) {
                return false;
            }
            return isValid(f.schema(), path, index + 1);
        }

        if (schema.getType() == Schema.Type.MAP) {
            // In a map, the segment represents a key; descend into value type
            return isValid(schema.getValueType(), path, index + 1);
        }

        if (schema.getType() == Schema.Type.ARRAY) {
            Schema elemSchema = schema.getElementType();
            if (segment.matches("\\d+")) {
                // Indexed access into array
                return isValid(elemSchema, path, index + 1);
            } else {
                // Flattened property search across elements of the array
                return isValid(elemSchema, path, index);
            }
        }

        return false;
    }
}
