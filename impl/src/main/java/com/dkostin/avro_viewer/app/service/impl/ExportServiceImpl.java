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
        List<Map<String, Object>> flatRows = new ArrayList<>(rows.size());
        LinkedHashSet<String> headerKeys = new LinkedHashSet<>();

        // Pass 1: Flatten & Collect Keys
        for (Map<String, Object> row : rows) {
            Map<String, Object> flatRow = new LinkedHashMap<>();
            com.dkostin.avro_viewer.app.util.MapFlattener.flatten("", row, flatRow);
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

    private String csvEscape(String s) {
        // RFC-ish: escape quotes, wrap if contains comma/quote/newline
        String escaped = s.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
