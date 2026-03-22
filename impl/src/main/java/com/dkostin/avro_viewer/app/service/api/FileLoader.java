package com.dkostin.avro_viewer.app.service.api;

import com.dkostin.avro_viewer.app.domain.model.Page;

import java.nio.file.Path;

public interface FileLoader {

    Page openFile(Path filePath) throws Exception;

    boolean isFileOpen();
}
