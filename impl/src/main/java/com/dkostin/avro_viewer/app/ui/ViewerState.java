package com.dkostin.avro_viewer.app.ui;

import lombok.Getter;
import lombok.Setter;
import org.apache.avro.Schema;

import java.nio.file.Path;

@Getter
public final class ViewerState {
    private Path file;

    @Setter
    private Schema schema;

    private int pageIndex;

    private int pageSize = 50; // default pageSize is 50

    @Setter
    private boolean hasNext;

    // filter state, selected fields, etc

    public void openFile(Path file) {
        this.file = file;
        this.schema = null;
        this.pageIndex = 0;
        this.hasNext = true;
    }

    public void setPageSize(int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be > 0");
        }
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

    // pageSize updated => reset paging
    public void resetToFirstPage() {
        this.pageIndex = 0;
        this.hasNext = true;
    }
}
