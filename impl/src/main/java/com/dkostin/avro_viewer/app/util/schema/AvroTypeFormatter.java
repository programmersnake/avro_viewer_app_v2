package com.dkostin.avro_viewer.app.util.schema;

import lombok.experimental.UtilityClass;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Produces compact, human-friendly type signatures for Avro schemas.
 */
@UtilityClass
public final class AvroTypeFormatter {

    public static String format(Schema schema) {
        if (schema == null) {
            return "";
        }

        if (schema.getType() == Schema.Type.UNION) {
            List<Schema> types = schema.getTypes();
            boolean nullable = types.stream().anyMatch(t -> t.getType() == Schema.Type.NULL);
            List<Schema> nonNull = types.stream()
                    .filter(t -> t.getType() != Schema.Type.NULL)
                    .toList();

            if (nonNull.isEmpty()) {
                return "null";
            }

            if (nonNull.size() == 1) {
                return formatSingle(nonNull.getFirst()) + (nullable ? "?" : "");
            }

            String branches = nonNull.stream()
                    .map(AvroTypeFormatter::formatCompact)
                    .collect(Collectors.joining(" | "));
            return "union<" + branches + ">" + (nullable ? "?" : "");
        }

        return formatSingle(schema);
    }

    /**
     * Formats a single (non-union) schema node, incorporating logical types where present.
     */
    public static String formatSingle(Schema schema) {
        if (schema == null) {
            return "";
        }

        // Logical types handling
        if (schema.getLogicalType() != null) {
            if (schema.getLogicalType() instanceof LogicalTypes.Decimal decimal) {
                return "decimal(" + decimal.getPrecision() + "," + decimal.getScale() + ")";
            }
            String base = schema.getType().getName().toLowerCase(Locale.ROOT);
            return base + " · " + schema.getLogicalType().getName();
        }

        return switch (schema.getType()) {
            case RECORD -> "record " + schema.getName();
            case ARRAY -> "array<" + formatCompact(schema.getElementType()) + ">";
            case MAP -> "map<" + formatCompact(schema.getValueType()) + ">";
            case ENUM -> "enum " + schema.getName();
            case FIXED -> "fixed(" + schema.getFixedSize() + ")";
            case UNION -> format(schema);
            case NULL -> "null";
            default -> schema.getType().getName().toLowerCase(Locale.ROOT);
        };
    }

    /**
     * Compact name representation when nested inside container types (e.g. array<Item> instead of array<record Item>).
     */
    public static String formatCompact(Schema schema) {
        if (schema == null) {
            return "";
        }
        if (schema.getType() == Schema.Type.RECORD) {
            return schema.getName();
        }
        if (schema.getType() == Schema.Type.UNION) {
            return format(schema);
        }
        if (schema.getLogicalType() != null) {
            if (schema.getLogicalType() instanceof LogicalTypes.Decimal decimal) {
                return "decimal(" + decimal.getPrecision() + "," + decimal.getScale() + ")";
            }
            return schema.getLogicalType().getName();
        }
        return switch (schema.getType()) {
            case ARRAY -> "array<" + formatCompact(schema.getElementType()) + ">";
            case MAP -> "map<" + formatCompact(schema.getValueType()) + ">";
            case ENUM -> schema.getName();
            case FIXED -> "fixed(" + schema.getFixedSize() + ")";
            case NULL -> "null";
            default -> schema.getType().getName().toLowerCase(Locale.ROOT);
        };
    }
}
