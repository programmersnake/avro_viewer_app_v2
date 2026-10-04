package com.dkostin.avro_viewer.app.service.api;

import com.dkostin.avro_viewer.app.domain.model.Page;
import com.dkostin.avro_viewer.app.domain.model.SearchControl;
import com.dkostin.avro_viewer.app.domain.model.SearchRequest;
import com.dkostin.avro_viewer.app.domain.model.SearchResult;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterGroup;

import java.util.List;

public interface SearchFacade {

    /**
     * FX thread. Captures the open file + schema and validates filter paths.
     * Throws InvalidFilterException if filter paths are invalid.
     */
    SearchRequest prepareSearch(List<FilterGroup> groups, int maxResults);

    /**
     * Any thread. Pure: does not read or modify viewer state.
     */
    SearchResult executeSearch(SearchRequest request, SearchControl control) throws Exception;

    default SearchResult executeSearch(SearchRequest request) throws Exception {
        return executeSearch(request, SearchControl.noop());
    }

    /**
     * FX thread. Enters search mode if request.file() is still the open file; returns false if stale.
     */
    boolean commitSearch(SearchRequest request, SearchResult result);

    Page clearSearch() throws Exception;

    boolean isSearchMode();

    int getMaxResults();

    int getDefaultMaxResults();
}
