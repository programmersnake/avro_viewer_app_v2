package com.dkostin.avro_viewer.app.service.api;

import com.dkostin.avro_viewer.app.domain.model.Page;
import com.dkostin.avro_viewer.app.domain.model.SearchResult;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterCriterion;
import javafx.beans.property.IntegerProperty;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface ViewerService {

    IntegerProperty maxResultsProperty();

    boolean isFileOpen();

    boolean isSearchMode();

    boolean hasNextPage();

    int getPageIndex();

    int getPageSize();

    void setPageSize(int pageSize);

    Page openFile(Path filePath) throws Exception;

    Page nextPage() throws Exception;

    Page prevPage() throws Exception;

    Page changePageSize(int newPageSize) throws Exception;

    SearchResult search(List<FilterCriterion> criteria, int maxResults) throws Exception;

    Page clearSearch() throws Exception;

    void exportToJson(Path out, ObservableList<Map<String, Object>> rows) throws IOException;

    void exportToCsv(Path out, ObservableList<Map<String, Object>> rows) throws IOException;

}
