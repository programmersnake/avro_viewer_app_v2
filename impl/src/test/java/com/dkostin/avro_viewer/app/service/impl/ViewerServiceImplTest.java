package com.dkostin.avro_viewer.app.service.impl;

import com.dkostin.avro_viewer.app.config.FilterPredicateFactory;
import com.dkostin.avro_viewer.app.config.FlatteningConfig;
import com.dkostin.avro_viewer.app.domain.model.*;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterCriterion;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterGroup;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterOption;
import com.dkostin.avro_viewer.app.domain.model.filter.MatchOperation;
import com.dkostin.avro_viewer.app.service.api.AvroFileService;
import com.dkostin.avro_viewer.app.service.api.ExportService;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ViewerServiceImplTest {

    private FakeAvroFileService fakeFileService;
    private FakeExportService fakeExportService;
    private ViewerServiceImpl viewerService;
    private Schema testSchema;

    @BeforeEach
    void setUp() {
        fakeFileService = new FakeAvroFileService();
        fakeExportService = new FakeExportService();
        FilterPredicateFactory predicateFactory = new FilterPredicateFactory();
        viewerService = new ViewerServiceImpl(fakeFileService, fakeExportService, predicateFactory);

        testSchema = SchemaBuilder.record("Test").fields()
                .requiredString("name")
                .requiredInt("age")
                .endRecord();
    }

    @Test
    void testOpenFileFailurePreservesPriorState() throws Exception {
        Path file1 = Path.of("file1.avro");
        fakeFileService.nextPageResult = new Page(testSchema, List.of(), true);
        viewerService.openFile(file1);

        assertTrue(viewerService.isFileOpen());
        assertEquals(file1, viewerService.getCurrentFile());

        Path file2 = Path.of("file2.avro");
        fakeFileService.throwOnRead = true;

        assertThrows(IOException.class, () -> viewerService.openFile(file2));

        // State remains file1
        assertEquals(file1, viewerService.getCurrentFile());
        assertEquals(0, viewerService.getPageIndex());
    }

    @Test
    void testNextPageFailurePreservesPriorPageIndex() throws Exception {
        Path file1 = Path.of("file1.avro");
        fakeFileService.nextPageResult = new Page(testSchema, List.of(), true);
        viewerService.openFile(file1);

        fakeFileService.throwOnRead = true;
        assertThrows(IOException.class, () -> viewerService.nextPage());

        assertEquals(0, viewerService.getPageIndex());
    }

    @Test
    void testPrevPageFailurePreservesPriorPageIndex() throws Exception {
        Path file1 = Path.of("file1.avro");
        fakeFileService.nextPageResult = new Page(testSchema, List.of(), true);
        viewerService.openFile(file1);

        // Advance to page 1
        viewerService.nextPage();
        assertEquals(1, viewerService.getPageIndex());

        // Attempt prevPage with failure
        fakeFileService.throwOnRead = true;
        assertThrows(IOException.class, () -> viewerService.prevPage());

        assertEquals(1, viewerService.getPageIndex());
    }

    @Test
    void testChangePageSizeFailurePreservesPriorState() throws Exception {
        Path file1 = Path.of("file1.avro");
        fakeFileService.nextPageResult = new Page(testSchema, List.of(), true);
        viewerService.openFile(file1);

        int originalPageSize = viewerService.getPageSize();
        fakeFileService.throwOnRead = true;
        assertThrows(IOException.class, () -> viewerService.changePageSize(100));

        assertEquals(originalPageSize, viewerService.getPageSize());
    }

    @Test
    void testPrepareExecuteAndCommitSearchLifecycle() throws Exception {
        Path file1 = Path.of("file1.avro");
        fakeFileService.nextPageResult = new Page(testSchema, List.of(), true);
        viewerService.openFile(file1);

        List<FilterGroup> groups = List.of(new FilterGroup(List.of(
                new FilterCriterion(FilterOption.ofField("name"), MatchOperation.EQUALS, "Alice")
        )));

        // 1. Prepare search
        SearchRequest req = viewerService.prepareSearch(groups, 100);
        assertEquals(file1, req.file());
        assertFalse(viewerService.isSearchMode(), "State should NOT change during prepareSearch");

        // 2. Execute search
        SearchResult result = viewerService.executeSearch(req);
        assertNotNull(result);
        assertFalse(viewerService.isSearchMode(), "State should NOT change during executeSearch");

        // 3. Commit search
        boolean committed = viewerService.commitSearch(req, result);
        assertTrue(committed);
        assertTrue(viewerService.isSearchMode(), "State should be in SEARCH mode after successful commit");
    }

    @Test
    void testCommitSearchStaleRejection() throws Exception {
        Path file1 = Path.of("file1.avro");
        fakeFileService.nextPageResult = new Page(testSchema, List.of(), true);
        viewerService.openFile(file1);

        List<FilterGroup> groups = List.of(new FilterGroup(List.of(
                new FilterCriterion(FilterOption.ofField("name"), MatchOperation.EQUALS, "Alice")
        )));
        SearchRequest reqFile1 = viewerService.prepareSearch(groups, 100);

        // In the meantime, user opens file2
        Path file2 = Path.of("file2.avro");
        viewerService.openFile(file2);

        SearchResult resultFile1 = viewerService.executeSearch(reqFile1);
        boolean committed = viewerService.commitSearch(reqFile1, resultFile1);

        assertFalse(committed, "commitSearch should reject result from stale file");
        assertFalse(viewerService.isSearchMode(), "State should remain in browse mode for file2");
        assertEquals(file2, viewerService.getCurrentFile());
    }

    @Test
    void testPrepareSearchValidatesFields() throws Exception {
        Path file1 = Path.of("file1.avro");
        fakeFileService.nextPageResult = new Page(testSchema, List.of(), true);
        viewerService.openFile(file1);

        List<FilterGroup> invalidGroups = List.of(new FilterGroup(List.of(
                new FilterCriterion(FilterOption.ofField("unknownProperty"), MatchOperation.EQUALS, "Val")
        )));

        assertThrows(InvalidFilterException.class, () -> viewerService.prepareSearch(invalidGroups, 100));
    }

    @Test
    void testReloadFileCallsInvalidateAndResets() throws Exception {
        Path file1 = Path.of("file1.avro");
        fakeFileService.nextPageResult = new Page(testSchema, List.of(), true);
        viewerService.openFile(file1);

        viewerService.nextPage();
        assertEquals(1, viewerService.getPageIndex());

        Page reloaded = viewerService.reloadFile();
        assertNotNull(reloaded);
        assertTrue(fakeFileService.invalidated);
        assertEquals(0, viewerService.getPageIndex());
        assertFalse(viewerService.isSearchMode());
    }

    @Test
    void testExportSnapshotCurrentViewVsAllMatching() throws Exception {
        Path file1 = Path.of("file1.avro");
        fakeFileService.nextPageResult = new Page(testSchema, List.of(), true);
        viewerService.openFile(file1);

        List<Map<String, Object>> visibleRows = List.of(
                Map.of("name", "Row1", "age", 25),
                Map.of("name", "Row2", "age", 30)
        );

        ExportSnapshot snapshot = viewerService.captureExportSnapshot(visibleRows);
        assertEquals(file1, snapshot.file());
        assertEquals(visibleRows, snapshot.currentRows());

        // CURRENT_VIEW samples
        List<String> currentSamples = viewerService.getSampleRecords(snapshot, ExportScope.CURRENT_VIEW, 10);
        assertEquals(2, currentSamples.size());
        assertTrue(currentSamples.getFirst().contains("Row1"));

        // ALL_MATCHING export
        viewerService.exportToCsvStreaming(Path.of("out.csv"), snapshot, ExportScope.ALL_MATCHING,
                new FlatteningConfig(true, true), ',', null);
        assertNotNull(fakeExportService.lastFactory);
    }

    private static class FakeAvroFileService implements AvroFileService {
        boolean throwOnRead = false;
        boolean invalidated = false;
        Page nextPageResult;

        @Override
        public Page readPage(Path file, int pageIndex, int pageSize) throws IOException {
            if (throwOnRead) {
                throw new IOException("Simulated read failure");
            }
            return nextPageResult != null ? nextPageResult : new Page(null, List.of(), false);
        }

        @Override
        public SearchResult search(Path file, List<FilterGroup> groups, int maxResults) throws Exception {
            if (throwOnRead) {
                throw new IOException("Simulated search failure");
            }
            return new SearchResult(null, List.of(), false, 0);
        }

        @Override
        public void invalidate() {
            invalidated = true;
        }
    }

    private static class FakeExportService implements ExportService {
        com.dkostin.avro_viewer.app.service.api.RecordProviderFactory lastFactory;

        @Override
        public void exportTableToJson(Path out, List<Map<String, Object>> rows) {}

        @Override
        public long exportToCsvStreaming(Path out, com.dkostin.avro_viewer.app.service.api.RecordProviderFactory providerFactory, FlatteningConfig config, char delimiter, ProgressListener listener) {
            this.lastFactory = providerFactory;
            return 0;
        }
    }
}
