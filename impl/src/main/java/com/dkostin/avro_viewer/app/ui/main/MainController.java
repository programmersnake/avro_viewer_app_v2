package com.dkostin.avro_viewer.app.ui.main;

import com.dkostin.avro_viewer.app.config.AppContext;
import com.dkostin.avro_viewer.app.domain.model.*;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterGroup;
import com.dkostin.avro_viewer.app.service.api.ExportFacade;
import com.dkostin.avro_viewer.app.service.api.FileLoader;
import com.dkostin.avro_viewer.app.service.api.PageNavigator;
import com.dkostin.avro_viewer.app.service.api.SearchFacade;
import com.dkostin.avro_viewer.app.ui.Theme;
import com.dkostin.avro_viewer.app.ui.component.*;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.converter.NumberStringConverter;
import org.apache.avro.Schema;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MainController:
 * - UI orchestration only
 * - Filters UI delegating to FiltersUi
 * - Table setup delegating to TableViewWindow
 * - Data/State operations delegating to segregated service interfaces
 */
public class MainController {

    /**
     * Hard sanity maximum for search results. Prevents OOM from unbounded
     * materialization of deep HashMaps when users enter excessively large values.
     */
    private static final int MAX_RESULTS_LIMIT = 100_000;

    // ---- Dependencies (segregated interfaces) ----
    private final FileLoader fileLoader;
    private final PageNavigator pageNavigator;
    private final SearchFacade searchFacade;
    private final ExportFacade exportFacade;
    private final RowViewWindow rowViewWindow;

    // ---- FXML ----
    @FXML
    private MenuItem reloadMenuItem;
    @FXML
    private TextField maxResultsField;
    @FXML
    private ToggleButton themeToggle;
    @FXML
    private ComboBox<Integer> pageSizeCombo;
    @FXML
    private VBox filtersBox;
    @FXML
    private Label resultsLabel;
    @FXML
    private Button prevBtn;
    @FXML
    private Button nextBtn;
    @FXML
    private Label pageLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private TableView<Map<String, Object>> table;
    private FiltersUi filtersUi;
    private TableViewWindow tableViewWindow;

    // ---- Controller-local UI state ----
    private final IntegerProperty maxResultsProperty = new SimpleIntegerProperty();
    private int lastSearchResultCount = -1;
    private Scene scene;
    private Task<?> activeSearchTask;
    private ExportPreviewDialog exportPreviewDialog;

    public MainController(AppContext ctx) {
        this.fileLoader = ctx.fileLoader();
        this.pageNavigator = ctx.pageNavigator();
        this.searchFacade = ctx.searchFacade();
        this.exportFacade = ctx.exportFacade();
        this.rowViewWindow = ctx.jsonWindow();
    }

    private static String safeSchemaName(Schema schema) {
        if (schema == null) {
            return "schema";
        }
        String name = schema.getName();
        return (name == null || name.isBlank()) ? "schema" : name;
    }

    /**
     * Called from Main.start(...) after FXMLLoader.load(), once Scene exists.
     */
    public void initTheme(Scene scene) {
        this.scene = scene;

        // default: dark
        themeToggle.setSelected(true);
        themeToggle.setTooltip(new Tooltip("Toggle Light/Dark theme"));

        applyTheme(themeToggle.isSelected());
    }

    // ---------------------------
    // Bindings / UI init
    // ---------------------------

    @FXML
    private void initialize() {
        // Components
        this.filtersUi = new FiltersUi(filtersBox);
        this.tableViewWindow = new TableViewWindow(table, rowViewWindow);

        // Initial UI
        initPageSizeCombo();
        filtersUi.clearFilters();

        bindMaxResultsField();
        resultsLabel.setText("Active: (none)");
        pageLabel.setText("Page 1");
        statusLabel.setText("");

        updateControls();
    }

    private void initPageSizeCombo() {
        pageSizeCombo.getItems().setAll(25, 50, 100, 200, 500);

        // prefer state value if present
        pageSizeCombo.setValue(pageNavigator.getPageSize());

        // keep service in sync
        pageSizeCombo.setOnAction(_ -> onPageSizeChanged());
    }

    // ---------------------------
    // Theme
    // ---------------------------

    private void bindMaxResultsField() {
        maxResultsProperty.set(searchFacade.getDefaultMaxResults());

        // Only digits in textfield + clamp to MAX_RESULTS_LIMIT
        maxResultsField.textProperty().addListener((_, _, newV) -> {
            if (newV == null) return;
            if (!newV.matches("\\d*")) {
                maxResultsField.setText(newV.replaceAll("[^\\d]", ""));
                return;
            }
            // Clamp excessively large values immediately on input
            if (!newV.isEmpty()) {
                try {
                    int parsed = Integer.parseInt(newV);
                    if (parsed > MAX_RESULTS_LIMIT) {
                        maxResultsField.setText(String.valueOf(MAX_RESULTS_LIMIT));
                        statusLabel.setText("Max results clamped to " + MAX_RESULTS_LIMIT);
                    }
                } catch (NumberFormatException ignored) {
                    // Oversized numeric literal — clamp defensively
                    maxResultsField.setText(String.valueOf(MAX_RESULTS_LIMIT));
                    statusLabel.setText("Max results clamped to " + MAX_RESULTS_LIMIT);
                }
            }
        });

        // Bidirectional binding (NumberStringConverter handles parse/format)
        maxResultsField.textProperty().bindBidirectional(
                maxResultsProperty,
                new NumberStringConverter()
        );
    }

    @FXML
    private void onThemeToggle() {
        if (scene == null) return;
        applyTheme(themeToggle.isSelected());
    }

    // ---------------------------
    // File
    // ---------------------------

    private void applyTheme(boolean darkSelected) {
        if (scene == null) return;

        scene.getStylesheets().clear();
        Theme t = darkSelected ? Theme.DARK : Theme.LIGHT;
        var css = getClass().getResource(t.getCssPath());
        if (css != null) {
            scene.getStylesheets().setAll(
                    getClass().getResource(Theme.BASE.getCssPath()).toExternalForm(),
                    css.toExternalForm()
            );
        }

        // propagate to JSON window
        rowViewWindow.syncStyles(scene.getStylesheets());
    }

    // ---------------------------
    // Filters & Search
    // ---------------------------

    @FXML
    private void onOpenFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Open .avro file");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Avro files (*.avro)", "*.avro"));

        File file = fc.showOpenDialog(table.getScene().getWindow());
        if (file == null) return;

        cancelActiveSearchIfRunning();

        executeWithUiUpdate("Error opening file: " + file.getPath(), () -> {
            Page page = fileLoader.openFile(file.toPath());

            // schema -> filters & table
            Schema schema = page.schema();
            filtersUi.updateFieldOptions(schema);
            tableViewWindow.updateTableData(page.records(), schema);

            // labels
            lastSearchResultCount = -1;
            resultsLabel.setText("Active: (none)");
            pageLabel.setText("Page 1");
            statusLabel.setText("Opened: " + file.getName() + " (" + page.records().size() + " records)");

            // page size combo must reflect service/state
            pageSizeCombo.setValue(pageNavigator.getPageSize());
        });
    }

    @FXML
    private void onReloadFile() {
        if (!fileLoader.isFileOpen()) return;

        cancelActiveSearchIfRunning();

        executeWithUiUpdate("Error reloading file", () -> {
            Page page = fileLoader.reloadFile();
            Schema schema = page.schema();
            filtersUi.updateFieldOptions(schema);
            tableViewWindow.updateTableData(page.records(), schema);

            lastSearchResultCount = -1;
            resultsLabel.setText("Active: (none)");
            pageLabel.setText("Page 1");
            Path current = fileLoader.getCurrentFile();
            String name = current != null ? current.getFileName().toString() : "file";
            statusLabel.setText("Reloaded: " + name + " (" + page.records().size() + " records)");
        });
    }

    @FXML
    private void onAddGroup(ActionEvent ignored) {
        filtersUi.addGroup();
    }

    @FXML
    private void onApplyFilters(ActionEvent ignored) {
        if (!fileLoader.isFileOpen()) {
            statusLabel.setText("Open an .avro file first");
            return;
        }

        cancelActiveSearchIfRunning();

        List<FilterGroup> groups = filtersUi.getFilterGroups();
        int max = safeMaxResults();

        SearchRequest request;
        try {
            request = searchFacade.prepareSearch(groups, max);
        } catch (InvalidFilterException ex) {
            ErrorAlert.showError("Invalid filter criteria", ex);
            statusLabel.setText("Unknown field(s): " + String.join(", ", ex.getInvalidPaths()));
            return;
        } catch (Exception ex) {
            ErrorAlert.showError("Failed to prepare search", ex);
            statusLabel.setText("Search preparation failed");
            return;
        }

        // UX: in-progress indication
        statusLabel.setText("Searching...");
        resultsLabel.setText("Searching...");

        Task<SearchResult> task = new Task<>() {
            @Override
            protected SearchResult call() throws Exception {
                return searchFacade.executeSearch(request);
            }
        };

        activeSearchTask = task;

        task.setOnSucceeded(_ -> {
            if (activeSearchTask != task) return;

            SearchResult result = task.getValue();
            boolean committed = searchFacade.commitSearch(request, result);
            if (!committed) {
                // Stale task result (e.g. user opened a new file while search was running)
                renderCommittedState();
                return;
            }

            tableViewWindow.updateSearchData(result.records(), result.schema());

            lastSearchResultCount = result.records().size();
            String tail = result.truncated() ? " (stopped by maxResults)" : "";
            resultsLabel.setText("Results: " + result.records().size() + tail);
            statusLabel.setText("Scanned: " + result.scanned() + ", matched: " + result.records().size() + tail);

            pageLabel.setText("Search");
            updateControls();
        });

        task.setOnFailed(_ -> {
            if (activeSearchTask != task) return;

            Throwable err = task.getException();
            ErrorAlert.showError("Search failed", err);
            renderCommittedState();
        });

        task.setOnCancelled(_ -> {
            if (activeSearchTask != task) return;
            renderCommittedState();
        });

        Thread t = new Thread(task, "avro-search");
        t.setDaemon(true);
        t.start();
    }

    private void renderCommittedState() {
        if (!fileLoader.isFileOpen()) {
            resultsLabel.setText("Active: (none)");
            pageLabel.setText("Page 1");
            statusLabel.setText("");
        } else if (searchFacade.isSearchMode()) {
            pageLabel.setText("Search");
            resultsLabel.setText(lastSearchResultCount >= 0 ? "Results: " + lastSearchResultCount : "Search mode");
            statusLabel.setText("Search mode active");
        } else {
            pageLabel.setText("Page " + (pageNavigator.getPageIndex() + 1));
            resultsLabel.setText("Active: (none)");
            Path current = fileLoader.getCurrentFile();
            String name = current != null ? current.getFileName().toString() : "file";
            int count = table.getItems() != null ? table.getItems().size() : 0;
            statusLabel.setText("Loaded " + count + " records from " + name);
        }
        updateControls();
    }

    // ---------------------------
    // Paging
    // ---------------------------

    @FXML
    private void onClearFilters(ActionEvent ignored) {
        cancelActiveSearchIfRunning();

        filtersUi.clearFilters();
        lastSearchResultCount = -1;
        resultsLabel.setText("Active: (none)");
        maxResultsProperty.set(searchFacade.getDefaultMaxResults());

        if (!fileLoader.isFileOpen()) {
            statusLabel.setText("");
            pageLabel.setText("Page 1");
            updateControls();
            return;
        }

        executeWithUiUpdate("Failed to reload after clearing filters", () -> {
            Page page = searchFacade.clearSearch();
            if (page != null) {
                tableViewWindow.updateTableData(page.records(), page.schema());
                pageLabel.setText("Page 1");
                statusLabel.setText("Loaded " + page.records().size() + " records from " + safeSchemaName(page.schema()));
            } else {
                statusLabel.setText("");
            }
        });
    }

    @FXML
    private void onPrevPage() {
        if (!fileLoader.isFileOpen()) return;
        if (searchFacade.isSearchMode()) return;

        cancelActiveSearchIfRunning();

        executeWithUiUpdate("Failed to load previous page", () -> {
            Page page = pageNavigator.prevPage();
            if (page != null) {
                tableViewWindow.updateTableData(page.records(), page.schema());
                pageLabel.setText("Page " + (pageNavigator.getPageIndex() + 1));
                statusLabel.setText("Loaded " + page.records().size() + " records (page " + (pageNavigator.getPageIndex() + 1) + ")");
            }
        });
    }

    @FXML
    private void onNextPage() {
        if (!fileLoader.isFileOpen()) return;
        if (searchFacade.isSearchMode()) return;

        cancelActiveSearchIfRunning();

        executeWithUiUpdate("Failed to load next page", () -> {
            Page page = pageNavigator.nextPage();
            if (page != null) {
                tableViewWindow.updateTableData(page.records(), page.schema());
                pageLabel.setText("Page " + (pageNavigator.getPageIndex() + 1));
                statusLabel.setText("Loaded " + page.records().size() + " records (page " + (pageNavigator.getPageIndex() + 1) + ")");
            }
        });
    }

    // ---------------------------
    // Export
    // ---------------------------

    private void onPageSizeChanged() {
        if (!fileLoader.isFileOpen()) {
            Integer ps = pageSizeCombo.getValue();
            if (ps != null) {
                pageNavigator.setPageSize(ps);
            }
            return;
        }

        cancelActiveSearchIfRunning();

        Integer newSize = pageSizeCombo.getValue();
        if (newSize == null) return;

        executeWithUiUpdate("Failed to change page size", () -> {
            Page page = pageNavigator.changePageSize(newSize);
            if (page != null) {
                tableViewWindow.updateTableData(page.records(), page.schema());
                pageLabel.setText("Page 1");
                statusLabel.setText("Loaded " + page.records().size() + " records from " + safeSchemaName(page.schema()));
            }
        });
    }

    @FXML
    private void onExportJson() {
        if (table.getItems() == null || table.getItems().isEmpty()) {
            statusLabel.setText("Nothing to export");
            return;
        }

        FileChooser fc = new FileChooser();
        fc.setTitle("Export to JSON");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON (*.json)", "*.json"));
        String suffix = searchFacade.isSearchMode() ? "search-results" : "page-" + (pageNavigator.getPageIndex() + 1);
        fc.setInitialFileName("export-" + suffix + ".json");

        File out = fc.showSaveDialog(table.getScene().getWindow());
        if (out == null) return;

        List<Map<String, Object>> rows = new ArrayList<>(table.getItems());
        executeWithUiUpdate("Export JSON failed", () -> {
            exportFacade.exportToJson(out.toPath(), rows);
            String scopeDesc = searchFacade.isSearchMode() ? "search results" : "page " + (pageNavigator.getPageIndex() + 1);
            statusLabel.setText("Exported " + rows.size() + " rows (" + scopeDesc + ") to " + out.getName());
        });
    }

    @FXML
    private void onExportCsv() {
        if (!fileLoader.isFileOpen() || table.getItems() == null || table.getItems().isEmpty()) {
            statusLabel.setText("Nothing to export");
            return;
        }
        List<Map<String, Object>> rows = new ArrayList<>(table.getItems());
        ExportSnapshot snapshot = exportFacade.captureExportSnapshot(rows);
        getOrCreateExportPreviewDialog().show(table.getScene(), snapshot);
    }

    private ExportPreviewDialog getOrCreateExportPreviewDialog() {
        if (exportPreviewDialog == null) {
            this.exportPreviewDialog = new ExportPreviewDialog(exportFacade);
        }
        return exportPreviewDialog;
    }

    // ---------------------------
    // Helpers
    // ---------------------------

    private void executeWithUiUpdate(String errorContext, UiAction action) {
        try {
            action.run();
        } catch (Exception ex) {
            ErrorAlert.showError(errorContext, ex);
            statusLabel.setText(errorContext);
        } finally {
            updateControls();
        }
    }

    private void updateControls() {
        boolean noFile = !fileLoader.isFileOpen();
        boolean searchMode = searchFacade.isSearchMode();

        prevBtn.setDisable(noFile || searchMode || pageNavigator.getPageIndex() == 0);
        nextBtn.setDisable(noFile || searchMode || !pageNavigator.hasNextPage());
        if (reloadMenuItem != null) {
            reloadMenuItem.setDisable(noFile);
        }
    }

    private int safeMaxResults() {
        String s = maxResultsField.getText();
        if (s == null || s.isBlank()) {
            maxResultsProperty.set(searchFacade.getDefaultMaxResults());
            return searchFacade.getDefaultMaxResults();
        }
        try {
            int v = Integer.parseInt(s);
            v = Math.max(1, v);
            if (v > MAX_RESULTS_LIMIT) {
                v = MAX_RESULTS_LIMIT;
                statusLabel.setText("Max results clamped to " + MAX_RESULTS_LIMIT);
            }
            maxResultsProperty.set(v);
            return v;
        } catch (NumberFormatException ex) {
            maxResultsProperty.set(searchFacade.getDefaultMaxResults());
            return searchFacade.getDefaultMaxResults();
        }
    }

    private void cancelActiveSearchIfRunning() {
        Task<?> task = activeSearchTask;
        if (task != null && task.isRunning()) {
            task.cancel(true);
        }
        activeSearchTask = null;
    }

    @FunctionalInterface
    private interface UiAction {
        void run() throws Exception;
    }
}
