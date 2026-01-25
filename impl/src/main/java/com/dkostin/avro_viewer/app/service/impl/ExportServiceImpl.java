package com.dkostin.avro_viewer.app.service.impl;

import com.dkostin.avro_viewer.app.service.api.ExportService;
import com.dkostin.avro_viewer.app.util.JsonSerializer;
import javafx.collections.ObservableList;
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
        List<String> headers = resolveHeaders(rows, schemaOrNull);

        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(out), StandardCharsets.UTF_8))) {
            // header
            w.write(String.join(",", headers.stream().map(this::csvEscape).toList()));
            w.newLine();

            // rows
            for (Map<String, Object> row : rows) {
                List<String> vals = new ArrayList<>(headers.size());
                for (String h : headers) {
                    Object v = row.get(h);
                    // normalize values same way as json
                    Object norm = normalizeForFlatText(v);
                    vals.add(csvEscape(norm == null ? "" : String.valueOf(norm)));
                }
                w.write(String.join(",", vals));
                w.newLine();
            }
        }
    }

    private static List<String> resolveHeaders(List<Map<String, Object>> rows, Schema schemaOrNull) {
        if (schemaOrNull != null) {
            List<String> h = new ArrayList<>();
            for (Schema.Field f : schemaOrNull.getFields()) h.add(f.name());
            return h;
        }
        // fallback: union of keys in insertion order
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (Map<String, Object> r : rows) set.addAll(r.keySet());
        return new ArrayList<>(set);
    }

    private Object normalizeForFlatText(Object v) {
        if (v == null) {
            return null;
        }

        if (v instanceof CharSequence cs) {
            return cs.toString();
        }
        if (v instanceof Enum<?> e) {
            return e.name();
        }

        // bytes -> short marker (CSV shouldn’t explode)
        if (v instanceof byte[] bytes) {
            return "bytes(" + bytes.length + ")";
        }
        if (v instanceof java.nio.ByteBuffer bb) {
            return "bytes(" + bb.remaining() + ")";
        }

        // collections/maps -> compact JSON string
        if (v instanceof Map<?, ?> || v instanceof Collection<?> || v instanceof org.apache.avro.generic.GenericRecord) {
            try {
                return JsonSerializer.toJsonSafe(v);
            } catch (Exception ignored) {
                return String.valueOf(v);
            }
        }

        return v;
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
