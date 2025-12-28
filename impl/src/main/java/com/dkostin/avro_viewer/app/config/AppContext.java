package com.dkostin.avro_viewer.app.config;

import com.dkostin.avro_viewer.app.data.AvroFileService;
import com.dkostin.avro_viewer.app.data.AvroFileServiceImpl;
import com.dkostin.avro_viewer.app.data.ExportService;
import com.dkostin.avro_viewer.app.data.ExportServiceImpl;
import com.dkostin.avro_viewer.app.logic.ViewerService;
import com.dkostin.avro_viewer.app.ui.component.JsonRowViewerWindow;
import com.dkostin.avro_viewer.app.domain.ViewerState;

public final class AppContext {

    private AvroFileService avroFileService;
    private ViewerState viewerState;
    private JsonRowViewerWindow jsonRowViewerWindow;
    private ExportService exportService;
    private FilterPredicateFactory filterPredicateFactory;
    private ViewerService viewerService;

    public AvroFileService avroFileService() {
        if (avroFileService == null) {
            avroFileService = new AvroFileServiceImpl(filterPredicateFactory());
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
        if (exportService == null) {
            exportService = new ExportServiceImpl();
        }
        return exportService;
    }

    public FilterPredicateFactory filterPredicateFactory() {
        if (filterPredicateFactory == null) {
            filterPredicateFactory = new FilterPredicateFactory();
        }
        return filterPredicateFactory;
    }

    public ViewerService viewerService() {
        if (viewerService == null) {
            viewerService = new ViewerService(avroFileService(), exportService(), viewerState());
        }
        return viewerService;
    }

}
