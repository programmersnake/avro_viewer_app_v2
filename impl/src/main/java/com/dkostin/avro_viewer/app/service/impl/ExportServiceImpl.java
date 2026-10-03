package com.dkostin.avro_viewer.app.service.impl;

import com.dkostin.avro_viewer.app.config.FlatteningConfig;
import com.dkostin.avro_viewer.app.service.api.ExportService;
import com.dkostin.avro_viewer.app.service.api.RecordProvider;
import com.dkostin.avro_viewer.app.service.api.RecordProviderFactory;
import com.dkostin.avro_viewer.app.util.JsonSerializer;
import com.dkostin.avro_viewer.app.util.StructuralFlatteningEngine;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SequenceWriter;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.csv.CsvMapper;
import tools.jackson.dataformat.csv.CsvSchema;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class ExportServiceImpl implements ExportService {

    @Override
    public void exportTableToJson(Path out, List<Map<String, Object>> rows) throws IOException {
        AtomicFileWriter.write(out, tmp -> {
            String json = JsonSerializer.toJsonSafe(rows);
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
        });
    }

    @Override
    public long exportToCsvStreaming(Path out, RecordProviderFactory providerFactory, FlatteningConfig config, char delimiter, ExportService.ProgressListener listener) throws IOException {
        long[] recordsWritten = new long[1];

        AtomicFileWriter.write(out, tmp -> {
            LinkedHashSet<String> headerKeys = new LinkedHashSet<>();
            ObjectMapper mapper = JsonMapper.builder()
                    .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .build();

            long count = 0;
            // Pass 1: Schema Construction (Streaming Scan)
            try (RecordProvider provider = providerFactory.create()) {
                while (provider.hasNext()) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedIOException("Export cancelled by user");
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
                throw new InterruptedIOException("Export cancelled by user");
            }

            if (headerKeys.isEmpty()) {
                Files.writeString(tmp, "", StandardCharsets.UTF_8);
                recordsWritten[0] = 0;
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
            try (BufferedWriter w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8);
                 SequenceWriter seqWriter = csvMapper.writer(csvSchema).writeValues(w)) {
                try (RecordProvider provider = providerFactory.create()) {
                    while (provider.hasNext()) {
                        if (Thread.currentThread().isInterrupted()) {
                            throw new InterruptedIOException("Export cancelled by user");
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

            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("Export cancelled by user");
            }

            recordsWritten[0] = current;
        });

        return recordsWritten[0];
    }
}
