package com.dkostin.avro_viewer.app.config;

import com.dkostin.avro_viewer.app.domain.state.ViewerState;
import com.dkostin.avro_viewer.app.service.api.AvroFileService;
import com.dkostin.avro_viewer.app.service.api.ExportService;
import com.dkostin.avro_viewer.app.service.api.ViewerService;
import com.dkostin.avro_viewer.app.service.impl.AvroFileServiceImpl;
import com.dkostin.avro_viewer.app.service.impl.ExportServiceImpl;
import com.dkostin.avro_viewer.app.service.impl.ViewerServiceImpl;
import com.dkostin.avro_viewer.app.ui.component.JsonRowViewerWindow;

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
            viewerService = new ViewerServiceImpl(avroFileService(), exportService(), viewerState());
        }
        return viewerService;
    }

}
