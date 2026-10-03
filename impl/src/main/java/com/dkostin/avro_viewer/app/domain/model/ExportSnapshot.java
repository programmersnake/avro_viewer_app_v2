package com.dkostin.avro_viewer.app.domain.model;

import com.dkostin.avro_viewer.app.domain.model.filter.FilterGroup;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable export context, captured on the FX thread when the export dialog opens.
 */
public record ExportSnapshot(
        Path file,
        List<FilterGroup> groups,
        boolean searchMode,
        int pageIndex,
        List<Map<String, Object>> currentRows) {

    public ExportSnapshot {
        Objects.requireNonNull(file, "file cannot be null");
        groups = groups == null ? List.of() : List.copyOf(groups);
        currentRows = currentRows == null ? List.of() : List.copyOf(currentRows);
    }

    /**
     * Human-readable description of current view, e.g. "page 3" or "search results".
     */
    public String viewDescription() {
        if (searchMode) {
            return "search results";
        }
        return "page " + (pageIndex + 1);
    }

    /**
     * File name suffix for exports, e.g. "page-3", "search-results", "all-records", "all-matching".
     */
    public String fileNameSuffix(ExportScope scope) {
        if (scope == ExportScope.CURRENT_VIEW) {
            return searchMode ? "search-results" : "page-" + (pageIndex + 1);
        } else {
            return searchMode ? "all-matching" : "all-records";
        }
    }

    /**
     * Detailed description of the chosen export scope.
     */
    public String scopeDescription(ExportScope scope) {
        if (scope == ExportScope.CURRENT_VIEW) {
            return "current view (" + viewDescription() + ")";
        } else {
            return searchMode ? "all records matching filters" : "all records in file";
        }
    }
}
