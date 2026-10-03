package com.dkostin.avro_viewer.app.service.api;

import com.dkostin.avro_viewer.app.config.FlatteningConfig;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface ExportService {

    @FunctionalInterface
    interface ProgressListener {
        void onProgress(int pass, long current, long total);
    }

    void exportTableToJson(Path out, List<Map<String, Object>> rows) throws IOException;

    long exportToCsvStreaming(Path out, RecordProviderFactory providerFactory,
                             FlatteningConfig config, char delimiter,
                             ProgressListener listener) throws IOException;
}
