package com.dkostin.avro_viewer.app.service.api;

import javafx.collections.ObservableList;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public interface ExportFacade {

    void exportToJson(Path out, ObservableList<Map<String, Object>> rows) throws IOException;

    void exportToCsv(Path out, ObservableList<Map<String, Object>> rows) throws IOException;
}
