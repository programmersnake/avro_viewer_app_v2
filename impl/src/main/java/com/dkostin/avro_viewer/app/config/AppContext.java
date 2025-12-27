package com.dkostin.avro_viewer.app.config;

import com.dkostin.avro_viewer.app.data.AvroFileService;
import com.dkostin.avro_viewer.app.data.AvroFileServiceImpl;
import com.dkostin.avro_viewer.app.ui.ViewerState;

public final class AppContext {

    private final AvroFileService avroFileService = new AvroFileServiceImpl();
    private final ViewerState viewerState = new ViewerState();

    public AvroFileService avroFileService() {
        return avroFileService;
    }

    public ViewerState viewerState() {
        return viewerState;
    }

}
