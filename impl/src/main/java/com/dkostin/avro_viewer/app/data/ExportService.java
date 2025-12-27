package com.dkostin.avro_viewer.app.data;

import javafx.collections.ObservableList;
import org.apache.avro.Schema;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public interface ExportService {

    void exportTableToJson(Path out, ObservableList<Map<String, Object>> rows) throws IOException;
    void exportTableToCsv(Path out, ObservableList<Map<String, Object>> rows, Schema schema) throws IOException;
}
