package com.dkostin.avro_viewer.app.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dkostin.avro_viewer.app.service.api.ExportService;
import com.dkostin.avro_viewer.app.util.JsonSerializer;
import javafx.collections.ObservableList;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ExportServiceImpl implements ExportService {
    @Override
    public void exportTableToJson(Path out, ObservableList<Map<String, Object>> rows) throws IOException {
        // JSON array of objects
        String json = JsonSerializer.toJsonSafe(rows);
        Files.writeString(out, json, StandardCharsets.UTF_8);
    }

    @Override
    public void exportTableToCsv(Path out, ObservableList<Map<String, Object>> rows, Schema schemaOrNull) throws IOException {
        List<Map<String, Object>> flatRows = new ArrayList<>(rows.size());
        LinkedHashSet<String> headerKeys = new LinkedHashSet<>();

        // Pass 1: Flatten & Collect Keys
        for (Map<String, Object> row : rows) {
            Map<String, Object> flatRow = new LinkedHashMap<>();
            flattenRow("", row, schemaOrNull, flatRow);
            flatRows.add(flatRow);
            headerKeys.addAll(flatRow.keySet());
        }

        // Pass 2: Write CSV
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(out), StandardCharsets.UTF_8))) {
            // Write headers
            w.write(String.join(",", headerKeys.stream().map(this::csvEscape).toList()));
            w.newLine();

            // Write flattened rows
            for (Map<String, Object> flatRow : flatRows) {
                List<String> vals = new ArrayList<>(headerKeys.size());
                for (String h : headerKeys) {
                    Object v = flatRow.get(h);
                    vals.add(csvEscape(v == null ? "" : String.valueOf(v)));
                }
                w.write(String.join(",", vals));
                w.newLine();
            }
        }
    }

    private void flattenRow(String currentPath, Object node, Schema currentSchema, Map<String, Object> flatRow) {
        if (node == null) {
            if (!currentPath.isEmpty()) {
                flatRow.put(currentPath, "");
            }
            return;
        }

        Schema actualSchema = unwrapUnion(currentSchema);

        // Base64 Decimals (bytes_b64 wrapper)
        if (node instanceof Map<?, ?> map && map.size() == 1 && map.containsKey("bytes_b64")) {
            Object b64 = map.get("bytes_b64");
            if (b64 instanceof String s) {
                if (actualSchema != null && actualSchema.getLogicalType() instanceof LogicalTypes.Decimal dec) {
                    byte[] decoded = Base64.getDecoder().decode(s);
                    java.math.BigDecimal bd = new java.math.BigDecimal(new java.math.BigInteger(decoded), dec.getScale());
                    flatRow.put(currentPath, bd.toPlainString());
                    return;
                }
            }
        }

        // byte[] or ByteBuffer directly (with decimal schema)
        if (node instanceof byte[] || node instanceof java.nio.ByteBuffer) {
            byte[] bytes = node instanceof byte[] b ? b : ((java.nio.ByteBuffer) node).array();
            if (actualSchema != null && actualSchema.getLogicalType() instanceof LogicalTypes.Decimal dec) {
                java.math.BigDecimal bd = new java.math.BigDecimal(new java.math.BigInteger(bytes), dec.getScale());
                flatRow.put(currentPath, bd.toPlainString());
                return;
            }
            flatRow.put(currentPath, "bytes(" + bytes.length + ")");
            return;
        }

        // Nested Map
        if (node instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                String newPath = currentPath.isEmpty() ? String.valueOf(k) : currentPath + "." + k;
                Schema childSchema = null;
                if (actualSchema != null) {
                    if (actualSchema.getType() == Schema.Type.RECORD) {
                        Schema.Field f = actualSchema.getField(String.valueOf(k));
                        if (f != null) childSchema = f.schema();
                    } else if (actualSchema.getType() == Schema.Type.MAP) {
                        childSchema = actualSchema.getValueType();
                    }
                }
                flattenRow(newPath, v, childSchema, flatRow);
            });
            return;
        }

        // GenericRecord
        if (node instanceof org.apache.avro.generic.GenericRecord rec) {
            Schema recSchema = rec.getSchema();
            for (Schema.Field f : recSchema.getFields()) {
                String newPath = currentPath.isEmpty() ? f.name() : currentPath + "." + f.name();
                flattenRow(newPath, rec.get(f.pos()), f.schema(), flatRow);
            }
            return;
        }

        // Arrays / Collections
        if (node instanceof Collection<?> coll) {
            if (coll.isEmpty()) {
                flatRow.put(currentPath, "");
                return;
            }
            // Check if it's an array of primitives
            Object first = coll.iterator().next();
            boolean isPrimitive = first instanceof CharSequence || first instanceof Number || first instanceof Boolean || first instanceof Enum;
            if (isPrimitive) {
                // Join with |
                String joined = coll.stream().map(String::valueOf).reduce((a, b) -> a + "|" + b).orElse("");
                flatRow.put(currentPath, joined);
            } else {
                // Complex objects -> JSON
                try {
                    String prettyJson = JsonSerializer.toJsonSafe(coll);
                    ObjectMapper compactMapper = new ObjectMapper();
                    JsonNode jsonNode = compactMapper.readTree(prettyJson);
                    flatRow.put(currentPath, compactMapper.writeValueAsString(jsonNode));
                } catch (Exception e) {
                    flatRow.put(currentPath, coll.toString());
                }
            }
            return;
        }

        // Strings / Primitives / Enums
        if (node instanceof CharSequence cs) {
            flatRow.put(currentPath, cs.toString());
            return;
        }
        if (node instanceof Enum<?> e) {
            flatRow.put(currentPath, e.name());
            return;
        }

        // Fallback for numbers, booleans etc.
        flatRow.put(currentPath, String.valueOf(node));
    }

    private Schema unwrapUnion(Schema schema) {
        if (schema != null && schema.getType() == Schema.Type.UNION) {
            for (Schema s : schema.getTypes()) {
                if (s.getType() != Schema.Type.NULL) return s;
            }
        }
        return schema;
    }

    private String csvEscape(String s) {
        // RFC-ish: escape quotes, wrap if contains comma/quote/newline
        String escaped = s.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
