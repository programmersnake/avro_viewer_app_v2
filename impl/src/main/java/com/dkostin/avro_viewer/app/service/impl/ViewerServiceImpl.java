package com.dkostin.avro_viewer.app.service.impl;

import com.dkostin.avro_viewer.app.config.FilterPathValidator;
import com.dkostin.avro_viewer.app.config.FilterPredicateFactory;
import com.dkostin.avro_viewer.app.config.FlatteningConfig;
import com.dkostin.avro_viewer.app.domain.model.ExportScope;
import com.dkostin.avro_viewer.app.domain.model.ExportSnapshot;
import com.dkostin.avro_viewer.app.domain.model.Page;
import com.dkostin.avro_viewer.app.domain.model.SearchRequest;
import com.dkostin.avro_viewer.app.domain.model.SearchResult;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterGroup;
import com.dkostin.avro_viewer.app.domain.state.ViewerState;
import com.dkostin.avro_viewer.app.service.api.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service orchestrating viewer state and coordinating Avro reading and exporting.
 * Uses atomic transitions on immutable ViewerState, committed only after operations succeed.
 */
public class ViewerServiceImpl implements FileLoader, PageNavigator, SearchFacade, ExportFacade {

    private final AvroFileService fileService;
    private final ExportService exportService;
    private final FilterPredicateFactory predicateFactory;
    private final AtomicReference<ViewerState> state;

    public ViewerServiceImpl(AvroFileService fileService, ExportService exportService, FilterPredicateFactory predicateFactory) {
        this(fileService, exportService, ViewerState.initial(), predicateFactory);
    }

    public ViewerServiceImpl(AvroFileService fileService, ExportService exportService, ViewerState initialState, FilterPredicateFactory predicateFactory) {
        this.fileService = Objects.requireNonNull(fileService, "fileService");
        this.exportService = Objects.requireNonNull(exportService, "exportService");
        this.predicateFactory = Objects.requireNonNull(predicateFactory, "predicateFactory");
        this.state = new AtomicReference<>(initialState != null ? initialState : ViewerState.initial());
    }

    // ---------------- FileLoader ----------------

    @Override
    public boolean isFileOpen() {
        return state.get().isFileOpen();
    }

    @Override
    public Path getCurrentFile() {
        return state.get().file();
    }

    @Override
    public Page openFile(Path filePath) throws Exception {
        ViewerState s = state.get();
        Page firstPage = fileService.readPage(filePath, 0, s.pageSize());
        state.updateAndGet(curr -> curr.browsing(filePath, 0, curr.pageSize(), firstPage));
        return firstPage;
    }

    @Override
    public Page reloadFile() throws Exception {
        ViewerState s = state.get();
        if (!s.isFileOpen()) {
            throw new IllegalStateException("No file is currently open");
        }
        fileService.invalidate();
        Page firstPage = fileService.readPage(s.file(), 0, s.pageSize());
        state.updateAndGet(curr -> curr.browsing(curr.file(), 0, curr.pageSize(), firstPage));
        return firstPage;
    }

    // ---------------- PageNavigator ----------------

    @Override
    public boolean hasNextPage() {
        return state.get().hasNext();
    }

    @Override
    public int getPageIndex() {
        return state.get().pageIndex();
    }

    @Override
    public int getPageSize() {
        return state.get().pageSize();
    }

    @Override
    public void setPageSize(int pageSize) {
        state.updateAndGet(curr -> curr.withPageSize(pageSize));
    }

    @Override
    public Page nextPage() throws Exception {
        ViewerState s = state.get();
        if (!s.isFileOpen() || s.isSearchMode() || !s.hasNext()) {
            return null;
        }
        int target = s.pageIndex() + 1;
        Page page = fileService.readPage(s.file(), target, s.pageSize());
        state.updateAndGet(curr -> {
            if (!Objects.equals(curr.file(), s.file()) || curr.isSearchMode()) {
                return curr;
            }
            return curr.browsing(curr.file(), target, curr.pageSize(), page);
        });
        return page;
    }

    @Override
    public Page prevPage() throws Exception {
        ViewerState s = state.get();
        if (!s.isFileOpen() || s.isSearchMode() || s.pageIndex() == 0) {
            return null;
        }
        int target = s.pageIndex() - 1;
        Page page = fileService.readPage(s.file(), target, s.pageSize());
        state.updateAndGet(curr -> {
            if (!Objects.equals(curr.file(), s.file()) || curr.isSearchMode()) {
                return curr;
            }
            return curr.browsing(curr.file(), target, curr.pageSize(), page);
        });
        return page;
    }

    @Override
    public Page changePageSize(int newPageSize) throws Exception {
        ViewerState s = state.get();
        if (!s.isFileOpen()) {
            state.updateAndGet(curr -> curr.withPageSize(newPageSize));
            return null;
        }
        Page page = fileService.readPage(s.file(), 0, newPageSize);
        state.updateAndGet(curr -> curr.browsing(curr.file(), 0, newPageSize, page));
        return page;
    }

    // ---------------- SearchFacade ----------------

    @Override
    public boolean isSearchMode() {
        return state.get().isSearchMode();
    }

    @Override
    public int getMaxResults() {
        return state.get().maxResults();
    }

    @Override
    public int getDefaultMaxResults() {
        return ViewerState.DEFAULT_MAX_RESULTS;
    }

    @Override
    public SearchRequest prepareSearch(List<FilterGroup> groups, int maxResults) {
        ViewerState s = state.get();
        if (!s.isFileOpen()) {
            throw new IllegalStateException("No file is currently open");
        }
        FilterPathValidator.validate(s.schema(), groups);
        return new SearchRequest(s.file(), s.schema(), groups, maxResults);
    }

    @Override
    public SearchResult executeSearch(SearchRequest request) throws Exception {
        Objects.requireNonNull(request, "request cannot be null");
        return fileService.search(request.file(), request.groups(), request.maxResults());
    }

    @Override
    public boolean commitSearch(SearchRequest request, SearchResult result) {
        Objects.requireNonNull(request, "request cannot be null");
        Objects.requireNonNull(result, "result cannot be null");
        while (true) {
            ViewerState current = state.get();
            if (!Objects.equals(current.file(), request.file())) {
                // Stale search result: user opened another file while search was in-flight
                return false;
            }
            ViewerState updated = current.searching(request.groups(), request.maxResults(), result.schema());
            if (state.compareAndSet(current, updated)) {
                return true;
            }
        }
    }

    @Override
    public Page clearSearch() throws Exception {
        ViewerState s = state.get();
        if (!s.isFileOpen()) {
            state.updateAndGet(curr -> curr.browsing(null, 0, curr.pageSize(), null));
            return null;
        }
        Page page = fileService.readPage(s.file(), 0, s.pageSize());
        state.updateAndGet(curr -> curr.browsing(curr.file(), 0, curr.pageSize(), page));
        return page;
    }

    // ---------------- ExportFacade ----------------

    @Override
    public void exportToJson(Path out, List<Map<String, Object>> rows) throws IOException {
        exportService.exportTableToJson(out, rows);
    }

    @Override
    public ExportSnapshot captureExportSnapshot(List<Map<String, Object>> currentRows) {
        ViewerState s = state.get();
        if (!s.isFileOpen()) {
            throw new IllegalStateException("No file is currently open");
        }
        return new ExportSnapshot(s.file(), s.groups(), s.isSearchMode(), s.pageIndex(), currentRows);
    }

    @Override
    public List<String> getSampleRecords(ExportSnapshot snapshot, ExportScope scope, int count) throws IOException {
        Objects.requireNonNull(snapshot, "snapshot cannot be null");
        RecordProviderFactory factory = createRecordProviderFactory(snapshot, scope);
        List<String> samples = new ArrayList<>(count);
        try (RecordProvider provider = factory.create()) {
            while (provider.hasNext() && samples.size() < count) {
                samples.add(provider.nextJsonRecord());
            }
        }
        return samples;
    }

    @Override
    public long exportToCsvStreaming(Path out, ExportSnapshot snapshot, ExportScope scope,
                                     FlatteningConfig config, char delimiter,
                                     ExportService.ProgressListener listener) throws IOException {
        Objects.requireNonNull(snapshot, "snapshot cannot be null");
        RecordProviderFactory factory = createRecordProviderFactory(snapshot, scope);
        return exportService.exportToCsvStreaming(out, factory, config, delimiter, listener);
    }

    private RecordProviderFactory createRecordProviderFactory(ExportSnapshot snapshot, ExportScope scope) {
        if (scope == ExportScope.CURRENT_VIEW) {
            return () -> new RowListRecordProvider(snapshot.currentRows());
        } else {
            return () -> new AvroRecordProvider(
                    snapshot.file(),
                    snapshot.searchMode() ? snapshot.groups() : List.of(),
                    predicateFactory
            );
        }
    }
}
