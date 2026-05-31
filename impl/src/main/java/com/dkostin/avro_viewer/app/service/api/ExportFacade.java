package com.dkostin.avro_viewer.app.service.api;

import com.dkostin.avro_viewer.app.config.FlatteningConfig;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface ExportFacade {

    void exportToJson(Path out, ObservableList<Map<String, Object>> rows) throws IOException;

    void exportToCsv(Path out, ObservableList<Map<String, Object>> rows) throws IOException;

    List<String> getSampleRecords(int count) throws IOException;

    void exportToCsvStreaming(Path out, FlatteningConfig config, char delimiter, ExportService.ProgressListener listener) throws IOException;
}
