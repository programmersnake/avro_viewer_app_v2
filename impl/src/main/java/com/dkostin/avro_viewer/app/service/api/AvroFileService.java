package com.dkostin.avro_viewer.app.service.api;

import com.dkostin.avro_viewer.app.domain.model.Page;
import com.dkostin.avro_viewer.app.domain.model.SearchResult;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterCriterion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface AvroFileService {
    Page readPage(Path file, int pageIndex, int pageSize) throws IOException;

    SearchResult search(Path file, List<FilterCriterion> criteria, int maxResults) throws Exception;
}
