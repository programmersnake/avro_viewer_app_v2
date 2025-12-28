package com.dkostin.avro_viewer.app.logic;

import com.dkostin.avro_viewer.app.data.AvroFileService;
import com.dkostin.avro_viewer.app.data.ExportService;
import com.dkostin.avro_viewer.app.domain.Page;
import com.dkostin.avro_viewer.app.domain.ViewerState;
import com.dkostin.avro_viewer.app.domain.filter.FilterCriterion;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ObservableList;
import org.apache.avro.Schema;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Service (Use-Case) for manipulation of state of viewing and handling AvroFileService
 */
public class ViewerService {
    private final AvroFileService fileService;
    private final ExportService exportService;
    private final ViewerState state;
    // Property for maxResults, handled and joined to UI text label
    private final IntegerProperty maxResultsProperty;

    public ViewerService(AvroFileService fileService, ExportService exportService, ViewerState state) {
        this.fileService = fileService;
        this.exportService = exportService;
        this.state = state;
        this.maxResultsProperty = new SimpleIntegerProperty(state.getMaxResults());
    }

    /**
     * Property for two-way binding to the maxResults text field
     */
    public IntegerProperty maxResultsProperty() {
        return maxResultsProperty;
    }

    /**
     * Checking if a file is open
     */
    public boolean isFileOpen() {
        return state.getFile() != null;
    }

    /**
     * Is search (filtering) mode activated?
     */
    public boolean isSearchMode() {
        return state.isSearchMode();
    }

    /**
     * Is there a next page when browsing by page?
     */
    public boolean hasNextPage() {
        return state.isHasNext();
    }

    /**
     * Current page index (0-based)
     */
    public int getPageIndex() {
        return state.getPageIndex();
    }

    public int getPageSize() {
        return state.getPageSize();
    }

    public void setPageSize(int pageSize) {
        state.setPageSize(pageSize);
    }

    /**
     * Current Avro schema (last read record)
     */
    public Schema getCurrentSchema() {
        return state.getSchema();
    }

    /**
     * Opens an Avro file and loads the first page of data.
     *
     * @return Page – a page object (first records of the file, page size state.pageSize).
     * @throws Exception if an error occurred while reading the file
     */
    public Page openFile(Path filePath) throws Exception {
        Path prevFile = state.getFile();
        try {
            state.openFile(filePath);

            Page firstPage = fileService.readPage(state.getFile(), state.getPageIndex(), state.getPageSize());

            state.setSchema(firstPage.schema());
            state.setHasNext(firstPage.hasNext());
            return firstPage;
        } catch (Exception ex) {
            // In case of failure – return the old state of the file
            state.openFile(prevFile);
            throw ex;
        }
    }

    /**
     * Goes to the next page (for pagination in view mode).
     *
     * @return Page – the page object after the jump.
     * @throws Exception if the page read failed
     */
    public Page nextPage() throws Exception {
        if (!state.isHasNext()) {
            // If there is no next page, we stay where we are
            return null;
        }
        state.nextPage();
        Page page = fileService.readPage(state.getFile(), state.getPageIndex(), state.getPageSize());
        state.setHasNext(page.hasNext());
        return page;
    }

    /**
     * Goes to the previous page (pagination)
     */
    public Page prevPage() throws Exception {
        if (state.getPageIndex() == 0) {
            return null;
        }
        state.prevPage();
        Page page = fileService.readPage(state.getFile(), state.getPageIndex(), state.getPageSize());
        state.setHasNext(page.hasNext());
        return page;
    }

    /**
     * Changes the page size (number of records) and reloads the first page.
     *
     * @return Page – new first page after resizing.
     * @throws Exception if read failed
     */
    public Page changePageSize(int newPageSize) throws Exception {
        state.setPageSize(newPageSize); // sets mode = BROWSE and pageIndex=0
        // After changing the page size – load the new first page
        Page page = fileService.readPage(state.getFile(), state.getPageIndex(), state.getPageSize());
        state.setSchema(page.schema());
        state.setHasNext(page.hasNext());
        return page;
    }

    /**
     * Starts a search (filtering) with the specified criteria.
     *
     * @param criteria   list of filtering criteria
     * @param maxResults maximum number of results
     * @return SearchResult – search result (found records, schema, counters, etc.)
     * @throws Exception if an error occurred during the search
     */
    public /*@Nullable*/ com.dkostin.avro_viewer.app.data.SearchResult search(List<FilterCriterion> criteria, int maxResults) throws Exception {
        state.setSearch(criteria, maxResults);            // switch state to SEARCH mode (pageIndex=0)
        maxResultsProperty.set(maxResults);               // synchronize the property with the new value
        // Search the file using AvroFileService
        return fileService.search(state.getFile(), criteria, maxResults);
    }

    /**
     * Resets search mode (returns to paginated view) and loads the first page.
     *
     * @return Page – the first page in browse mode after resetting filters.
     * @throws Exception if page reading failed
     */
    public Page clearSearch() throws Exception {
        state.clearSearch();  // resets criteria, maxResults=500, mode=BROWSE, pageIndex=0
        maxResultsProperty.set(state.getMaxResults());  // reset the bound maxResults value to 500
        // Return to the first page of the full file
        if (state.getFile() != null) {
            Page page = fileService.readPage(state.getFile(), 0, state.getPageSize());
            state.setSchema(page.schema());
            state.setHasNext(page.hasNext());
            return page;
        }
        return null;
    }

    public void exportToJson(Path out, ObservableList<Map<String, Object>> rows) throws IOException {
        exportService.exportTableToJson(out, rows);
    }

    public void exportToCsv(Path out, ObservableList<Map<String, Object>> rows) throws IOException {
        exportService.exportTableToCsv(out, rows, state.getSchema());
    }
}
