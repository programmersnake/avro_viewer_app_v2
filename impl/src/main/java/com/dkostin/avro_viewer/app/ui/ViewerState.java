package com.dkostin.avro_viewer.app.ui;

import com.dkostin.avro_viewer.app.filter.FilterCriterion;
import lombok.Getter;
import lombok.Setter;
import org.apache.avro.Schema;

import java.nio.file.Path;
import java.util.List;

@Getter
public final class ViewerState {
    private Path file;

    @Setter
    private Schema schema;

    private int pageIndex;

    private int pageSize = 50; // default pageSize is 50

    @Setter
    private boolean hasNext;

    private ViewMode mode = ViewMode.BROWSE;
    private List<FilterCriterion> criteria = List.of();
    private int maxResults = 500;


    public void openFile(Path file) {
        this.file = file;
        this.schema = null;
        this.pageIndex = 0;
        this.hasNext = true;
        this.mode = ViewMode.BROWSE;
    }

    public void setPageSize(int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be > 0");
        }
        this.mode = ViewMode.BROWSE;
        this.pageSize = pageSize;
        resetToFirstPage();
    }

    public void nextPage() {
        if (!hasNext) {
            return;
        }
        pageIndex++;
    }

    public void prevPage() {
        if (pageIndex > 0) {
            pageIndex--;
        }
    }

    public void setSearch(List<FilterCriterion> criteria, int maxResults) {
        this.criteria = List.copyOf(criteria);
        this.maxResults = maxResults;
        this.mode = ViewMode.SEARCH;
        resetToFirstPage();
    }

    public void clearSearch() {
        this.criteria = List.of();
        this.maxResults = 500;
        this.mode = ViewMode.BROWSE;
        resetToFirstPage();
    }

    // pageSize updated => reset paging
    public void resetToFirstPage() {
        this.pageIndex = 0;
        this.hasNext = true;
    }

}
