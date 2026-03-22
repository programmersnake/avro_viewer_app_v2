package com.dkostin.avro_viewer.app.config;

import com.dkostin.avro_viewer.app.domain.state.ViewerState;
import com.dkostin.avro_viewer.app.service.api.ExportFacade;
import com.dkostin.avro_viewer.app.service.api.FileLoader;
import com.dkostin.avro_viewer.app.service.api.PageNavigator;
import com.dkostin.avro_viewer.app.service.api.SearchFacade;
import com.dkostin.avro_viewer.app.service.impl.AvroFileServiceImpl;
import com.dkostin.avro_viewer.app.service.impl.ExportServiceImpl;
import com.dkostin.avro_viewer.app.service.impl.ViewerServiceImpl;
import com.dkostin.avro_viewer.app.ui.component.RowViewWindow;

public final class AppContext {

    private final ViewerState viewerState;
    private final RowViewWindow rowViewWindow;
    private final ViewerServiceImpl viewerService;

    public AppContext() {
        this.viewerState = new ViewerState();
        this.rowViewWindow = new RowViewWindow();

        var filterPredicateFactory = new FilterPredicateFactory();
        var avroFileService = new AvroFileServiceImpl(filterPredicateFactory);
        var exportService = new ExportServiceImpl();

        this.viewerService = new ViewerServiceImpl(avroFileService, exportService, viewerState);
    }

    public RowViewWindow jsonWindow() {
        return rowViewWindow;
    }

    public FileLoader fileLoader() {
        return viewerService;
    }

    public PageNavigator pageNavigator() {
        return viewerService;
    }

    public SearchFacade searchFacade() {
        return viewerService;
    }

    public ExportFacade exportFacade() {
        return viewerService;
    }
}

