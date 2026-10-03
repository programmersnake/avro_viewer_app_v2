package com.dkostin.avro_viewer.app.domain.state;

import com.dkostin.avro_viewer.app.domain.model.Page;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterGroup;
import org.apache.avro.Schema;

import java.nio.file.Path;
import java.util.List;

/**
 * Immutable representation of the viewer session state.
 */
public record ViewerState(
        Path file,
        Schema schema,
        int pageIndex,
        int pageSize,
        boolean hasNext,
        ViewMode mode,
        List<FilterGroup> groups,
        int maxResults
) {
    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int DEFAULT_MAX_RESULTS = 500;

    public ViewerState {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be > 0");
        }
        groups = groups == null ? List.of() : List.copyOf(groups);
    }

    public static ViewerState initial() {
        return new ViewerState(null, null, 0, DEFAULT_PAGE_SIZE, false, ViewMode.BROWSE, List.of(), DEFAULT_MAX_RESULTS);
    }

    /**
     * Transition to browse mode with a successfully loaded page. Clears active search.
     */
    public ViewerState browsing(Path file, int pageIndex, int pageSize, Page page) {
        return new ViewerState(
                file,
                page != null ? page.schema() : this.schema,
                pageIndex,
                pageSize,
                page != null && page.hasNext(),
                ViewMode.BROWSE,
                List.of(),
                DEFAULT_MAX_RESULTS
        );
    }

    /**
     * Transition to search mode after a search query has finished successfully.
     */
    public ViewerState searching(List<FilterGroup> groups, int maxResults, Schema schema) {
        return new ViewerState(
                this.file,
                schema != null ? schema : this.schema,
                0,
                this.pageSize,
                false,
                ViewMode.SEARCH,
                groups,
                maxResults
        );
    }

    /**
     * Updates default page size (used when no file is open or before loading).
     */
    public ViewerState withPageSize(int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be > 0");
        }
        return new ViewerState(this.file, this.schema, this.pageIndex, pageSize, this.hasNext, this.mode, this.groups, this.maxResults);
    }

    public boolean isFileOpen() {
        return file != null;
    }

    public boolean isSearchMode() {
        return mode == ViewMode.SEARCH;
    }

    // Compatibility getters
    public Path getFile() { return file; }
    public Schema getSchema() { return schema; }
    public int getPageIndex() { return pageIndex; }
    public int getPageSize() { return pageSize; }
    public boolean isHasNext() { return hasNext; }
    public ViewMode getMode() { return mode; }
    public List<FilterGroup> getGroups() { return groups; }
    public int getMaxResults() { return maxResults; }
}
