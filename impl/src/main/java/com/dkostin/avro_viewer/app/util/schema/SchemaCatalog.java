package com.dkostin.avro_viewer.app.util.schema;

import com.dkostin.avro_viewer.app.domain.model.fileinfo.FieldRule;
import com.dkostin.avro_viewer.app.domain.model.fileinfo.NodeKind;
import com.dkostin.avro_viewer.app.domain.model.fileinfo.SchemaNode;
import lombok.experimental.UtilityClass;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;

import java.util.*;

/**
 * Traverses Avro schemas to produce hierarchical tree structures (for File Info)
 * and flattened dot-notation paths (for filter suggestions and validation).
 */
@UtilityClass
public final class SchemaCatalog {

    public static final int MAX_DEPTH = 10;
    public static final int MAX_NODES = 5000;

    /**
     * Builds a hierarchical tree representation of the schema.
     */
    public static SchemaNode tree(Schema root) {
        if (root == null) {
            return null;
        }

        NodeContext ctx = new NodeContext();
        return buildNode("", "", 0, root, null, ctx);
    }

    /**
     * Returns a flat list of all selectable paths in schema order, matching the syntax
     * accepted by FilterPathValidator and FilterPredicateFactory.
     */
    public static List<SchemaNode> paths(Schema root) {
        if (root == null) {
            return List.of();
        }

        SchemaNode rootNode = tree(root);
        if (rootNode == null) {
            return List.of();
        }

        List<SchemaNode> result = new ArrayList<>();
        collectPaths(rootNode, result);
        return Collections.unmodifiableList(result);
    }

    private static void collectPaths(SchemaNode node, List<SchemaNode> out) {
        if (node == null) return;
        if (!node.path().isEmpty()) {
            out.add(node);
        }
        for (SchemaNode child : node.children()) {
            collectPaths(child, out);
        }
    }

    private static SchemaNode buildNode(String name,
                                        String path,
                                        int depth,
                                        Schema schema,
                                        Schema.Field field,
                                        NodeContext ctx) {
        if (ctx.nodeCount++ >= MAX_NODES || depth > MAX_DEPTH) {
            return null;
        }

        boolean nullable = isNullable(schema);
        Schema effectiveSchema = unwrapNullable(schema);
        NodeKind kind = determineKind(effectiveSchema);
        String typeDisplay = AvroTypeFormatter.format(schema);
        String defaultValue = extractDefaultValue(field);
        String doc = extractDoc(field, schema);
        List<FieldRule> rules = extractRules(field, schema, effectiveSchema, nullable, defaultValue, doc);

        // Check for recursive record reference
        boolean isRecord = effectiveSchema != null && effectiveSchema.getType() == Schema.Type.RECORD;
        String recordFullName = isRecord ? effectiveSchema.getFullName() : null;
        boolean recursiveRef = isRecord && ctx.activeRecords.contains(recordFullName);

        List<SchemaNode> children = new ArrayList<>();
        if (!recursiveRef && effectiveSchema != null) {
            if (isRecord) {
                ctx.activeRecords.push(recordFullName);
                for (Schema.Field f : effectiveSchema.getFields()) {
                    String childName = f.name();
                    String childPath = path.isEmpty() ? childName : path + "." + childName;
                    SchemaNode childNode = buildNode(childName, childPath, depth + 1, f.schema(), f, ctx);
                    if (childNode != null) {
                        children.add(childNode);
                    }
                }
                ctx.activeRecords.pop();
            } else if (effectiveSchema.getType() == Schema.Type.ARRAY) {
                Schema elemSchema = effectiveSchema.getElementType();
                Schema effectiveElem = unwrapNullable(elemSchema);
                if (effectiveElem != null && effectiveElem.getType() == Schema.Type.RECORD) {
                    String elemFullName = effectiveElem.getFullName();
                    boolean elemRecursive = ctx.activeRecords.contains(elemFullName);
                    if (!elemRecursive) {
                        ctx.activeRecords.push(elemFullName);
                        for (Schema.Field f : effectiveElem.getFields()) {
                            String childName = f.name();
                            String childPath = path.isEmpty() ? childName : path + "." + childName;
                            SchemaNode childNode = buildNode(childName, childPath, depth + 1, f.schema(), f, ctx);
                            if (childNode != null) {
                                children.add(childNode);
                            }
                        }
                        ctx.activeRecords.pop();
                    }
                }
            } else if (effectiveSchema.getType() == Schema.Type.UNION) {
                // Multi-branch union
                for (Schema branch : effectiveSchema.getTypes()) {
                    if (branch.getType() == Schema.Type.NULL) continue;
                    Schema effectiveBranch = unwrapNullable(branch);
                    if (effectiveBranch != null && effectiveBranch.getType() == Schema.Type.RECORD) {
                        String branchFullName = effectiveBranch.getFullName();
                        boolean branchRecursive = ctx.activeRecords.contains(branchFullName);
                        if (!branchRecursive) {
                            ctx.activeRecords.push(branchFullName);
                            for (Schema.Field f : effectiveBranch.getFields()) {
                                String childName = f.name();
                                String childPath = path.isEmpty() ? childName : path + "." + childName;
                                SchemaNode childNode = buildNode(childName, childPath, depth + 1, f.schema(), f, ctx);
                                if (childNode != null) {
                                    children.add(childNode);
                                }
                            }
                            ctx.activeRecords.pop();
                        }
                    }
                }
            }
        }

        String unionBranch = null;
        if (field != null && field.schema().getType() == Schema.Type.UNION) {
            List<Schema> nonNull = field.schema().getTypes().stream()
                    .filter(t -> t.getType() != Schema.Type.NULL)
                    .toList();
            if (nonNull.size() > 1) {
                unionBranch = effectiveSchema != null ? effectiveSchema.getName() : null;
            }
        }

        return new SchemaNode(
                name,
                path,
                depth,
                kind,
                typeDisplay,
                nullable,
                defaultValue,
                doc,
                rules,
                children,
                recursiveRef,
                unionBranch
        );
    }

    public static boolean isNullable(Schema schema) {
        if (schema == null) return false;
        if (schema.getType() == Schema.Type.UNION) {
            return schema.getTypes().stream().anyMatch(t -> t.getType() == Schema.Type.NULL);
        }
        return schema.getType() == Schema.Type.NULL;
    }

    private static Schema unwrapNullable(Schema schema) {
        if (schema == null) return null;
        if (schema.getType() == Schema.Type.UNION) {
            List<Schema> nonNull = schema.getTypes().stream()
                    .filter(t -> t.getType() != Schema.Type.NULL)
                    .toList();
            if (nonNull.size() == 1) {
                return nonNull.getFirst();
            }
        }
        return schema;
    }

    private static NodeKind determineKind(Schema schema) {
        if (schema == null) return NodeKind.PRIMITIVE;
        return switch (schema.getType()) {
            case RECORD -> NodeKind.RECORD;
            case ARRAY -> NodeKind.ARRAY;
            case MAP -> NodeKind.MAP;
            case ENUM -> NodeKind.ENUM;
            case FIXED -> NodeKind.FIXED;
            case UNION -> NodeKind.UNION;
            default -> NodeKind.PRIMITIVE;
        };
    }

    private static String extractDefaultValue(Schema.Field field) {
        if (field == null) return null;
        try {
            if (!field.hasDefaultValue()) return null;
            Object val = field.defaultVal();
            if (val == null) return "null";
            return String.valueOf(val);
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractDoc(Schema.Field field, Schema schema) {
        if (field != null && field.doc() != null && !field.doc().isBlank()) {
            return field.doc();
        }
        if (schema != null && schema.getDoc() != null && !schema.getDoc().isBlank()) {
            return schema.getDoc();
        }
        return "";
    }

    private static List<FieldRule> extractRules(Schema.Field field,
                                                Schema fieldSchema,
                                                Schema effectiveSchema,
                                                boolean nullable,
                                                String defaultValue,
                                                String doc) {
        List<FieldRule> rules = new ArrayList<>();

        // Nullability / Required
        rules.add(new FieldRule("Required", nullable ? "No (nullable)" : "Yes"));

        // Default value
        if (defaultValue != null) {
            rules.add(new FieldRule("Default value", defaultValue));
        }

        if (effectiveSchema != null) {
            // Enum symbols
            if (effectiveSchema.getType() == Schema.Type.ENUM) {
                rules.add(new FieldRule("Enum symbols", String.join(", ", effectiveSchema.getEnumSymbols())));
            }

            // Fixed size
            if (effectiveSchema.getType() == Schema.Type.FIXED) {
                rules.add(new FieldRule("Fixed size", effectiveSchema.getFixedSize() + " bytes"));
            }

            // Logical types
            if (effectiveSchema.getLogicalType() != null) {
                var logical = effectiveSchema.getLogicalType();
                if (logical instanceof LogicalTypes.Decimal dec) {
                    rules.add(new FieldRule("Decimal precision", String.valueOf(dec.getPrecision())));
                    rules.add(new FieldRule("Decimal scale", String.valueOf(dec.getScale())));
                } else {
                    String explanation = switch (logical.getName()) {
                        case "timestamp-millis" -> "Milliseconds from Unix epoch (UTC)";
                        case "timestamp-micros" -> "Microseconds from Unix epoch (UTC)";
                        case "time-millis" -> "Milliseconds past midnight";
                        case "time-micros" -> "Microseconds past midnight";
                        case "date" -> "Days from Unix epoch";
                        case "uuid" -> "Universally Unique Identifier (UUID)";
                        default -> "";
                    };
                    rules.add(new FieldRule("Logical type", explanation.isEmpty() ? logical.getName() : logical.getName() + " (" + explanation + ")"));
                }
            }

            // Custom schema props
            for (Map.Entry<String, Object> prop : effectiveSchema.getObjectProps().entrySet()) {
                rules.add(new FieldRule("Schema prop: " + prop.getKey(), String.valueOf(prop.getValue())));
            }
        }

        // Aliases
        if (field != null && field.aliases() != null && !field.aliases().isEmpty()) {
            rules.add(new FieldRule("Aliases", String.join(", ", field.aliases())));
        }

        // Sort order
        if (field != null && field.order() != null && field.order() != Schema.Field.Order.ASCENDING) {
            rules.add(new FieldRule("Sort order", field.order().name()));
        }

        // Custom field props
        if (field != null) {
            for (Map.Entry<String, Object> prop : field.getObjectProps().entrySet()) {
                rules.add(new FieldRule("Field prop: " + prop.getKey(), String.valueOf(prop.getValue())));
            }
        }

        // Documentation
        if (doc != null && !doc.isBlank()) {
            rules.add(new FieldRule("Documentation", doc));
        }

        return Collections.unmodifiableList(rules);
    }

    private static class NodeContext {
        int nodeCount = 0;
        final Deque<String> activeRecords = new ArrayDeque<>();
    }
}
