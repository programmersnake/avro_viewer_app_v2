package com.dkostin.avro_viewer.app.data;

import com.dkostin.avro_viewer.app.common.Page;
import org.apache.avro.Schema;

import java.io.IOException;
import java.nio.file.Path;

public interface AvroFileService {
    Schema readSchema(Path file) throws IOException;
    Page readPage(Path file, int pageIndex, int pageSize) throws IOException;
}
