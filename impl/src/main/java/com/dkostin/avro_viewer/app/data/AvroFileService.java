package com.dkostin.avro_viewer.app.data;

import com.dkostin.avro_viewer.app.common.Page;
import com.dkostin.avro_viewer.app.filter.FilterCriterion;
import org.apache.avro.Schema;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface AvroFileService {
    Schema readSchema(Path file) throws IOException;
    Page readPage(Path file, int pageIndex, int pageSize) throws IOException;

    SearchResult search(Path file, List<FilterCriterion> criteria, int maxResults) throws Exception;
}
