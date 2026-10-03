package com.dkostin.avro_viewer.app.service.api;

import com.dkostin.avro_viewer.app.config.FlatteningConfig;
import com.dkostin.avro_viewer.app.domain.model.ExportScope;
import com.dkostin.avro_viewer.app.domain.model.ExportSnapshot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface ExportFacade {

    void exportToJson(Path out, List<Map<String, Object>> rows) throws IOException;

    /**
     * FX thread. Freezes the current file, filter groups, search mode, page index, and visible rows.
     */
    ExportSnapshot captureExportSnapshot(List<Map<String, Object>> currentRows);

    /**
     * Any thread. Returns sample records as JSON strings for dialog preview generation.
     */
    List<String> getSampleRecords(ExportSnapshot snapshot, ExportScope scope, int count) throws IOException;

    /**
     * Any thread. Returns the total count of records written.
     */
    long exportToCsvStreaming(Path out, ExportSnapshot snapshot, ExportScope scope,
                             FlatteningConfig config, char delimiter,
                             ExportService.ProgressListener listener) throws IOException;
}
