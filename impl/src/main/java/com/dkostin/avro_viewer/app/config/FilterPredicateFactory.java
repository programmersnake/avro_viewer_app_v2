package com.dkostin.avro_viewer.app.config;

import com.dkostin.avro_viewer.app.domain.model.filter.FilterCriterion;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterGroup;
import com.dkostin.avro_viewer.app.util.DeepSearchEngine;
import com.dkostin.avro_viewer.app.util.PreparedMatcher;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.IndexedRecord;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class FilterPredicateFactory {

    private record ResolvedNode(Object value, Schema schema) {}

    /**
     * Compiles filter criteria into a predicate on {@link GenericRecord}.
     * <p>
     * All predicates use {@link DeepSearchEngine} for evaluation, which natively
     * traverses Avro types ({@code IndexedRecord}, {@code GenericArray}) without
     * any normalization overhead.
     * <ul>
     *   <li>Wildcard ({@link FilterCriterion#isWildcard()}): passes the entire
     *       {@code GenericRecord} as the DFS root.</li>
     *   <li>Specific field: passes {@code rec.get(field)} as the DFS root,
     *       enabling deep search into nested records, arrays, and maps.</li>
     * </ul>
     */
    public Predicate<GenericRecord> compile(List<FilterGroup> groups) {
        if (groups == null || groups.isEmpty()) {
            return _ -> true;
        }

        List<Predicate<GenericRecord>> groupPredicates = new ArrayList<>();
        for (FilterGroup group : groups) {
            if (group.isEmpty()) continue;

            List<Predicate<GenericRecord>> preds = new ArrayList<>();
            for (FilterCriterion c : group.criteria()) {
                preds.add(toPredicate(c));
            }

            groupPredicates.add(rec -> {
                for (var p : preds) {
                    if (!p.test(rec)) return false;
                }
                return true;
            });
        }

        if (groupPredicates.isEmpty()) return _ -> true;
        if (groupPredicates.size() == 1) return groupPredicates.getFirst();

        // OR between groups
        return rec -> {
            for (var gp : groupPredicates) {
                if (gp.test(rec)) return true;
            }
            return false;
        };
    }

    private Predicate<GenericRecord> toPredicate(FilterCriterion c) {
        var op = c.op();
        Object raw = c.value();
        PreparedMatcher matcher = new PreparedMatcher(op, raw);

        if (c.isWildcard()) {
            // Wildcard: DFS the entire record
            return rec -> DeepSearchEngine.matches(rec, rec.getSchema(), matcher);
        }

        String fieldName = c.fieldName();
        if (fieldName == null) {
            return _ -> false;
        }

        // Support dot-notation path querying
        if (fieldName.contains(".")) {
            String[] path = fieldName.split("\\.");
            return rec -> {
                ResolvedNode resolved = resolvePath(rec, rec.getSchema(), path, 0);
                return DeepSearchEngine.matches(resolved.value(), resolved.schema(), matcher);
            };
        }

        // Specific field at root level: DFS into that field's subtree
        return rec -> {
            Schema.Field f = rec.getSchema().getField(fieldName);
            Schema fieldSchema = f != null ? f.schema() : null;
            return DeepSearchEngine.matches(rec.get(fieldName), fieldSchema, matcher);
        };
    }

    private static ResolvedNode resolvePath(Object node, Schema schema, String[] path, int index) {
        if (node == null || index >= path.length) {
            return new ResolvedNode(node, schema);
        }
        String segment = path[index];
        Schema unwrappedSchema = unwrapUnion(schema);

        if (node instanceof IndexedRecord rec) {
            Schema recSchema = unwrappedSchema != null ? unwrappedSchema : rec.getSchema();
            Schema.Field f = recSchema.getField(segment);
            if (f == null) return new ResolvedNode(null, null);
            return resolvePath(rec.get(f.pos()), f.schema(), path, index + 1);
        }
        if (node instanceof Map<?, ?> map) {
            Schema valSchema = unwrappedSchema != null && unwrappedSchema.getType() == Schema.Type.MAP
                    ? unwrappedSchema.getValueType()
                    : null;
            return resolvePath(map.get(segment), valSchema, path, index + 1);
        }
        if (node instanceof List<?> list) {
            try {
                int idx = Integer.parseInt(segment);
                if (idx >= 0 && idx < list.size()) {
                    Schema elemSchema = unwrappedSchema != null && unwrappedSchema.getType() == Schema.Type.ARRAY
                            ? unwrappedSchema.getElementType()
                            : null;
                    return resolvePath(list.get(idx), elemSchema, path, index + 1);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (node instanceof Collection<?> coll) {
            Schema elemSchema = unwrappedSchema != null && unwrappedSchema.getType() == Schema.Type.ARRAY
                    ? unwrappedSchema.getElementType()
                    : null;
            List<Object> results = new java.util.ArrayList<>();
            for (Object item : coll) {
                ResolvedNode res = resolvePath(item, elemSchema, path, index);
                if (res.value() != null) {
                    if (res.value() instanceof Collection<?> subColl) {
                        results.addAll(subColl);
                    } else {
                        results.add(res.value());
                    }
                }
            }
            return new ResolvedNode(results.isEmpty() ? null : results, elemSchema);
        }
        return new ResolvedNode(null, null);
    }

    private static Schema unwrapUnion(Schema schema) {
        if (schema != null && schema.getType() == Schema.Type.UNION) {
            for (Schema s : schema.getTypes()) {
                if (s.getType() != Schema.Type.NULL) return s;
            }
        }
        return schema;
    }
}





