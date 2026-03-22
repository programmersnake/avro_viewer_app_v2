package com.dkostin.avro_viewer.app.service.api;

import com.dkostin.avro_viewer.app.domain.model.Page;
import com.dkostin.avro_viewer.app.domain.model.SearchResult;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterCriterion;
import javafx.beans.property.IntegerProperty;

import java.util.List;

public interface SearchFacade {

    SearchResult search(List<FilterCriterion> criteria, int maxResults) throws Exception;

    Page clearSearch() throws Exception;

    boolean isSearchMode();

    IntegerProperty maxResultsProperty();
}
