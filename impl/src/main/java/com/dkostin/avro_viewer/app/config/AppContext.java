package com.dkostin.avro_viewer.app.config;

import com.dkostin.avro_viewer.app.data.AvroFileService;
import com.dkostin.avro_viewer.app.data.AvroFileServiceImpl;
import com.dkostin.avro_viewer.app.data.ExportService;
import com.dkostin.avro_viewer.app.data.ExportServiceImpl;
import com.dkostin.avro_viewer.app.ui.JsonRowViewerWindow;
import com.dkostin.avro_viewer.app.ui.ViewerState;

public final class AppContext {

    private AvroFileService avroFileService;
    private ViewerState viewerState;
    private JsonRowViewerWindow jsonRowViewerWindow;
    private ExportService exportService;

    public AvroFileService avroFileService() {
        if (avroFileService == null) {
            avroFileService = new AvroFileServiceImpl();
        }
        return avroFileService;
    }

    public ViewerState viewerState() {
        if (viewerState == null) {
            viewerState = new ViewerState();
        }
        return viewerState;
    }

    public JsonRowViewerWindow jsonWindow() {
        if (jsonRowViewerWindow == null) {
            jsonRowViewerWindow = new JsonRowViewerWindow();
        }
        return jsonRowViewerWindow;
    }

    public ExportService exportService() {
        if(exportService == null) {
            exportService = new ExportServiceImpl();
        }
        return exportService;
    }

}
