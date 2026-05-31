package com.dkostin.avro_viewer.app.service.impl;

import com.dkostin.avro_viewer.app.config.FlatteningConfig;
import com.dkostin.avro_viewer.app.service.api.ExportService;
import com.dkostin.avro_viewer.app.service.api.RecordProvider;
import com.dkostin.avro_viewer.app.service.api.RecordProviderFactory;
import com.dkostin.avro_viewer.app.util.JsonSerializer;
import com.dkostin.avro_viewer.app.util.StructuralFlatteningEngine;
import javafx.collections.ObservableList;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SequenceWriter;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.csv.CsvMapper;
import tools.jackson.dataformat.csv.CsvSchema;

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
    public void exportTableToCsv(Path out, ObservableList<Map<String, Object>> rows) throws IOException {
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

    @Override
    public void exportToCsvStreaming(Path out, RecordProviderFactory providerFactory, FlatteningConfig config, char delimiter, ExportService.ProgressListener listener) throws IOException {
        try {
            LinkedHashSet<String> headerKeys = new LinkedHashSet<>();
            ObjectMapper mapper = JsonMapper.builder().build();

            long count = 0;
            // Pass 1: Schema Construction (Streaming Scan)
            try (RecordProvider provider = providerFactory.create()) {
                while (provider.hasNext()) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new IOException("Export cancelled by user");
                    }
                    String json = provider.nextJsonRecord();
                    JsonNode node = mapper.readTree(json);
                    Map<String, String> flatRow = StructuralFlatteningEngine.flatten(node, config);
                    headerKeys.addAll(flatRow.keySet());
                    count++;
                    if (listener != null) {
                        listener.onProgress(1, count, -1);
                    }
                }
            }

            if (Thread.currentThread().isInterrupted()) {
                throw new IOException("Export cancelled by user");
            }

            if (headerKeys.isEmpty()) {
                Files.writeString(out, "", StandardCharsets.UTF_8);
                return;
            }

            // Pass 2: Value Output (Streaming Write) using jackson-dataformat-csv
            CsvMapper csvMapper = new CsvMapper();
            CsvSchema.Builder schemaBuilder = CsvSchema.builder();
            for (String h : headerKeys) {
                schemaBuilder.addColumn(h);
            }

            CsvSchema csvSchema = schemaBuilder.build()
                    .withHeader()
                    .withColumnSeparator(delimiter);

            long current = 0;
            try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8);
                 SequenceWriter seqWriter = csvMapper.writer(csvSchema).writeValues(w)) {
                try (RecordProvider provider = providerFactory.create()) {
                    while (provider.hasNext()) {
                        if (Thread.currentThread().isInterrupted()) {
                            throw new IOException("Export cancelled by user");
                        }
                        String json = provider.nextJsonRecord();
                        JsonNode node = mapper.readTree(json);
                        Map<String, String> flatRow = StructuralFlatteningEngine.flatten(node, config);
                        seqWriter.write(flatRow);
                        current++;
                        if (listener != null) {
                            listener.onProgress(2, current, count);
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            if (Thread.currentThread().isInterrupted()) {
                try {
                    Files.deleteIfExists(out);
                } catch (IOException ignored) {}
            }
            throw e;
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
