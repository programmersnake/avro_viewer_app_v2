package com.dkostin.avro_viewer.app.util;

import lombok.experimental.UtilityClass;
import org.apache.avro.Schema;
import org.apache.avro.UnresolvedUnionException;
import org.apache.avro.generic.GenericData;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public final class AvroUnions {

    /**
     * Resolves the appropriate branch schema in a union based on the actual value.
     * If schema is not a UNION, returns it unchanged.
     */
    public static Schema resolveBranch(Schema schema, Object value) {
        if (schema == null || schema.getType() != Schema.Type.UNION) {
            return schema;
        }

        if (value == null) {
            for (Schema s : schema.getTypes()) {
                if (s.getType() == Schema.Type.NULL) {
                    return s;
                }
            }
            return schema;
        }

        try {
            int branch = GenericData.get().resolveUnion(schema, value);
            return schema.getTypes().get(branch);
        } catch (UnresolvedUnionException | NullPointerException | ClassCastException e) {
            // Fallback for non-Avro structures (e.g. normalized Map or List) without Avro identity
            return firstNonNull(schema);
        }
    }

    /**
     * Returns the first non-null branch in a union schema, or the schema itself if not a union.
     */
    public static Schema firstNonNull(Schema schema) {
        if (schema != null && schema.getType() == Schema.Type.UNION) {
            for (Schema s : schema.getTypes()) {
                if (s.getType() != Schema.Type.NULL) {
                    return s;
                }
            }
        }
        return schema;
    }

    /**
     * Returns all non-null branch schemas if the schema is a union, or a single-element list otherwise.
     */
    public static List<Schema> nonNullBranches(Schema schema) {
        if (schema == null) {
            return List.of();
        }
        if (schema.getType() != Schema.Type.UNION) {
            return List.of(schema);
        }
        List<Schema> res = new ArrayList<>();
        for (Schema s : schema.getTypes()) {
            if (s.getType() != Schema.Type.NULL) {
                res.add(s);
            }
        }
        return res;
    }
}
