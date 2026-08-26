package com.dkostin.avro_viewer.app.service.api;

import com.dkostin.avro_viewer.app.domain.model.Page;
import com.dkostin.avro_viewer.app.domain.model.SearchResult;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterGroup;
import javafx.beans.property.IntegerProperty;

import java.util.List;

public interface SearchFacade {

    SearchResult search(List<FilterGroup> groups, int maxResults) throws Exception;

    Page clearSearch() throws Exception;

    boolean isSearchMode();

    IntegerProperty maxResultsProperty();
}
